// How Long Since sync server: a web UI, POST /sync (plaintext, used by the web UI until it learns the encrypted protocol),
// and GET/PUT /s/{id}: encrypted blobs the server can't read, one file per synced group.
// Config (env): DATA_FILE (default /data/data.json), ADDR (default :8080), TOKEN (bearer token, required unless INSECURE=1).
package main

import (
	"compress/gzip"
	"crypto/sha256"
	"crypto/subtle"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"reflect"
	"regexp"
	"slices"
	"strings"
	"sync"
	"time"
)

type Counter struct {
	ID            string  `json:"id"` // UUID
	Label         string  `json:"label"`
	Tags          string  `json:"tags"`
	NotifyAfterMs *int64  `json:"notifyAfterMs"`
	NfcTagID      *string `json:"nfcTagId"`
	UpdatedAt     int64   `json:"updatedAt"` // last-write-wins
	Deleted       bool    `json:"deleted"`   // tombstone
	Archived      bool    `json:"archived"`
}

type Event struct {
	ID        string `json:"id"` // UUID
	CounterID string `json:"counterId"`
	At        int64  `json:"at"`
	Source    string `json:"source"` // create | manual | nfc
	Deleted   bool   `json:"deleted"`
	Note      string `json:"note,omitempty"` // set when logged, never edited
}

type State struct {
	Counters []Counter `json:"counters"`
	Events   []Event   `json:"events"`
}

// merge folds in into s: counters by newest updatedAt, events by union (deletion is sticky).
func merge(s, in State) State {
	cs := map[string]Counter{}
	for _, c := range append(s.Counters, in.Counters...) {
		if old, ok := cs[c.ID]; !ok || c.UpdatedAt > old.UpdatedAt {
			cs[c.ID] = c
		}
	}
	es := map[string]Event{}
	for _, e := range append(s.Events, in.Events...) {
		if old, ok := es[e.ID]; ok {
			e.Deleted = e.Deleted || old.Deleted
			if e.Note == "" {
				e.Note = old.Note // a client that predates notes mustn't erase them
			}
		}
		es[e.ID] = e
	}
	out := State{Counters: []Counter{}, Events: []Event{}}
	for _, c := range cs {
		out.Counters = append(out.Counters, c)
	}
	for _, e := range es {
		out.Events = append(out.Events, e)
	}
	// Sorted, so an unchanged state compares equal and the file stays stable.
	slices.SortFunc(out.Counters, func(a, b Counter) int { return strings.Compare(a.ID, b.ID) })
	slices.SortFunc(out.Events, func(a, b Event) int { return strings.Compare(a.ID, b.ID) })
	return out
}

// tombstoneTTL: deleted counters older than this are dropped for good, with their events.
// A device offline for longer could bring such a counter back.
const tombstoneTTL = 90 * 24 * time.Hour

// prune drops counters deleted before cutoff (ms) and events without a counter.
func prune(s State, cutoff int64) State {
	out := State{Counters: []Counter{}, Events: []Event{}}
	keep := map[string]bool{}
	for _, c := range s.Counters {
		if !c.Deleted || c.UpdatedAt >= cutoff {
			out.Counters = append(out.Counters, c)
			keep[c.ID] = true
		}
	}
	for _, e := range s.Events {
		if keep[e.CounterID] {
			out.Events = append(out.Events, e)
		}
	}
	return out
}

//go:embed index.html
var index []byte

//go:embed icon.svg
var icon []byte

const manifest = `{"name":"How Long Since?","short_name":"How Long Since","start_url":".","display":"standalone",
"background_color":"#3366cc","theme_color":"#3366cc","icons":[{"src":"icon.svg","sizes":"any","type":"image/svg+xml"}]}`

var (
	mu    sync.Mutex // guards state and the file
	state State
	file  = env("DATA_FILE", "/data/data.json")
)

func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}

func load() State {
	s := State{Counters: []Counter{}, Events: []Event{}}
	if b, err := os.ReadFile(file); err == nil {
		if err := json.Unmarshal(b, &s); err != nil {
			log.Fatalf("corrupt %s: %v", file, err) // never overwrite unreadable data
		}
	}
	return s
}

func save(s State) error {
	b, _ := json.MarshalIndent(s, "", " ")
	tmp := file + ".tmp"
	if err := os.WriteFile(tmp, b, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, file) // atomic replace
}

var (
	idRe        = regexp.MustCompile(`^[0-9a-f]{32}$`) // space ids are hex, which also rules out path tricks
	blobMu      sync.Mutex                             // guards the blob files
	errConflict = errors.New("etag mismatch")
)

func etagOf(b []byte) string { h := sha256.Sum256(b); return hex.EncodeToString(h[:16]) }

func blobPath(dir, id string) string { return filepath.Join(dir, "s", id) }

// getBlob returns the stored bytes and their etag; os.ErrNotExist if there is none.
func getBlob(dir, id string) ([]byte, string, error) {
	blobMu.Lock()
	defer blobMu.Unlock()
	b, err := os.ReadFile(blobPath(dir, id))
	return b, etagOf(b), err
}

// putBlob stores b if ifMatch is the current etag; "" means create only. errConflict otherwise.
func putBlob(dir, id string, b []byte, ifMatch string) (string, error) {
	blobMu.Lock()
	defer blobMu.Unlock()
	old, err := os.ReadFile(blobPath(dir, id))
	switch {
	case errors.Is(err, os.ErrNotExist):
		if ifMatch != "" {
			return "", errConflict
		}
	case err != nil:
		return "", err
	case ifMatch != etagOf(old):
		return "", errConflict
	}
	p := blobPath(dir, id)
	if err := os.MkdirAll(filepath.Dir(p), 0o700); err != nil {
		return "", err
	}
	if err := os.WriteFile(p+".tmp", b, 0o600); err != nil {
		return "", err
	}
	return etagOf(b), os.Rename(p+".tmp", p) // atomic replace
}

func main() {
	token := os.Getenv("TOKEN")
	if token == "" {
		if os.Getenv("INSECURE") != "1" {
			log.Fatal("TOKEN is not set; set it, or INSECURE=1 to run without auth (anyone who can reach the server can read and change the data)")
		}
		log.Print("WARNING: INSECURE=1, running without auth")
	}
	state = load() // kept in memory; fails fast on a corrupt file
	static := func(path, typ string, body []byte) {
		http.HandleFunc("GET "+path, func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", typ)
			w.Write(body)
		})
	}
	static("/{$}", "text/html; charset=utf-8", index)
	static("/icon.svg", "image/svg+xml", icon)
	static("/manifest.webmanifest", "application/manifest+json", []byte(manifest))
	http.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })
	authed := func(h http.HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			if token != "" && subtle.ConstantTimeCompare([]byte(r.Header.Get("Authorization")), []byte("Bearer "+token)) != 1 {
				http.Error(w, "unauthorized", 401)
				return
			}
			h(w, r)
		}
	}
	dir := filepath.Dir(file)
	http.HandleFunc("GET /s/{id}", authed(func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		if !idRe.MatchString(id) {
			http.Error(w, "bad id", 400)
			return
		}
		b, etag, err := getBlob(dir, id)
		if errors.Is(err, os.ErrNotExist) {
			http.Error(w, "not found", 404)
			return
		} else if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"etag": etag, "ciphertext": b}) // []byte is base64 in JSON
	}))
	http.HandleFunc("PUT /s/{id}", authed(func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		var in struct {
			Ciphertext []byte `json:"ciphertext"`
		}
		if !idRe.MatchString(id) {
			http.Error(w, "bad id", 400)
			return
		}
		if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 10<<20)).Decode(&in); err != nil || len(in.Ciphertext) == 0 {
			http.Error(w, "bad body", 400)
			return
		}
		etag, err := putBlob(dir, id, in.Ciphertext, r.Header.Get("If-Match"))
		if errors.Is(err, errConflict) {
			http.Error(w, "conflict", 409)
			return
		} else if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{"etag": etag})
	}))
	http.HandleFunc("POST /sync", authed(func(w http.ResponseWriter, r *http.Request) {
		var in State
		if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 10<<20)).Decode(&in); err != nil {
			http.Error(w, err.Error(), 400)
			return
		}
		mu.Lock()
		defer mu.Unlock()
		s := prune(merge(state, in), time.Now().Add(-tombstoneTTL).UnixMilli())
		if !reflect.DeepEqual(s, state) { // most syncs change nothing: skip the write
			if err := save(s); err != nil {
				http.Error(w, err.Error(), 500)
				return
			}
			state = s
		}
		w.Header().Set("Content-Type", "application/json")
		if strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
			w.Header().Set("Content-Encoding", "gzip")
			gz := gzip.NewWriter(w)
			defer gz.Close()
			json.NewEncoder(gz).Encode(s)
			return
		}
		json.NewEncoder(w).Encode(s)
	}))
	addr := env("ADDR", ":8080")
	log.Printf("listening on %s, data %s", addr, file)
	srv := &http.Server{Addr: addr, ReadHeaderTimeout: 10 * time.Second, ReadTimeout: 30 * time.Second,
		WriteTimeout: 30 * time.Second, IdleTimeout: 2 * time.Minute} // drop slow or stuck clients
	log.Fatal(srv.ListenAndServe())
}
