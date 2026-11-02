// How Long Since sync server: one JSON file, a web UI, and POST /sync.
// Config (env): DATA_FILE (default /data/data.json), ADDR (default :8080), TOKEN (optional bearer token).
package main

import (
	_ "embed"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"sync"
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
	return out
}

//go:embed index.html
var index []byte

var (
	mu   sync.Mutex
	file = env("DATA_FILE", "/data/data.json")
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
	load() // fail fast on a corrupt file
	http.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Write(index)
	})
	http.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })
	http.HandleFunc("POST /sync", func(w http.ResponseWriter, r *http.Request) {
		if token != "" && r.Header.Get("Authorization") != "Bearer "+token {
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
		s := merge(load(), in)
		if err := save(s); err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(s)
	})
	addr := env("ADDR", ":8080")
	log.Printf("listening on %s, data %s", addr, file)
	log.Fatal(http.ListenAndServe(addr, nil))
}
