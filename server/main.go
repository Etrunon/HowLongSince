// How Long Since sync server: the web app, and encrypted blobs at GET/PUT /s/{id} (one file per synced group) that it cannot read.
// Config (env): DATA_DIR (default /data), ADDR (default :8080), TOKEN (bearer token, required unless INSECURE=1).
package main

import (
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
	"regexp"
	"sync"
	"time"
)

//go:embed index.html
var index []byte

//go:embed icon.svg
var icon []byte

const manifest = `{"name":"How Long Since?","short_name":"How Long Since","start_url":".","display":"standalone",
"background_color":"#3366cc","theme_color":"#3366cc","icons":[{"src":"icon.svg","sizes":"any","type":"image/svg+xml"}]}`

func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
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
	dir := env("DATA_DIR", "/data")
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
	addr := env("ADDR", ":8080")
	log.Printf("listening on %s, data %s", addr, dir)
	srv := &http.Server{Addr: addr, ReadHeaderTimeout: 10 * time.Second, ReadTimeout: 30 * time.Second,
		WriteTimeout: 30 * time.Second, IdleTimeout: 2 * time.Minute} // drop slow or stuck clients
	log.Fatal(srv.ListenAndServe())
}
