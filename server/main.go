// How Long Since sync server: one JSON file, a web UI, and POST /sync.
// Config (env): DATA_FILE (default /data/data.json), ADDR (default :8080), TOKEN (optional bearer token).
package main

import (
	"compress/gzip"
	"crypto/subtle"
	_ "embed"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"reflect"
	"slices"
	"strings"
	"sync"
	"time"
)

type Counter struct {
	ID            string `json:"id"` // UUID
	Label         string `json:"label"`
	Tags          string `json:"tags"`
	NotifyAfterMs *int64 `json:"notifyAfterMs"`
	NfcTagID      *string `json:"nfcTagId"`
	UpdatedAt     int64  `json:"updatedAt"` // last-write-wins
	Deleted       bool   `json:"deleted"`   // tombstone
}

type Event struct {
	ID        string `json:"id"` // UUID
	CounterID string `json:"counterId"`
	At        int64  `json:"at"`
	Source    string `json:"source"` // create | manual | nfc
	Deleted   bool   `json:"deleted"`
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

func main() {
	token := os.Getenv("TOKEN")
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
	http.HandleFunc("POST /sync", func(w http.ResponseWriter, r *http.Request) {
		if token != "" && subtle.ConstantTimeCompare([]byte(r.Header.Get("Authorization")), []byte("Bearer "+token)) != 1 {
			http.Error(w, "unauthorized", 401)
			return
		}
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
	})
	addr := env("ADDR", ":8080")
	log.Printf("listening on %s, data %s", addr, file)
	log.Fatal(http.ListenAndServe(addr, nil))
}
