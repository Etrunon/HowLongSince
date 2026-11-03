// How Long Since sync server: the web app, and encrypted group blobs at GET/PUT /s/{id} (one file per group) that it cannot read.
// Each group has its own devices, each with a random token: admins invite, revoke and list; members sync and leave.
// A recovery password (stretched on the client, only its hash stored) enrolls or promotes an admin. See PLAN.md.
// Config (env): DATA_DIR (default /data), ADDR (default :8080), TOKEN (optional: legacy groups and /stats),
// CREATES_PER_IP_HOUR (default 10), MAX_GROUPS (default 10000), MAX_ADMIN_GROUPS (default 10, per device).
package main

import (
	"bytes"
	"compress/gzip"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"io/fs"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed web
var web embed.FS

const manifest = `{"name":"How Long Since?","short_name":"How Long Since","start_url":".","display":"standalone",
"background_color":"#3366cc","theme_color":"#3366cc","icons":[{"src":"icon.svg","sizes":"any","type":"image/svg+xml"}]}`

func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}

func envInt(k string, d int) int {
	if n, err := strconv.Atoi(os.Getenv(k)); err == nil {
		return n
	}
	return d
}

var (
	idRe   = regexp.MustCompile(`^[0-9a-f]{32}$`)       // group ids are hex, which also rules out path tricks
	devRe  = regexp.MustCompile(`^[0-9A-Za-z-]{1,64}$`) // device ids are UUIDs
	blobMu sync.Mutex                                   // guards the blob files; every request on a group holds it
)

const (
	hour        = int64(time.Hour / time.Second)
	day         = 24 * hour
	inviteTTL   = day
	maxFails    = 20 // failed logins and enrolls per IP per hour
	maxRecovery = 5  // failed recovery passwords per group per hour
)

func etagOf(b []byte) string { h := sha256.Sum256(b); return hex.EncodeToString(h[:16]) }
func hash(s string) string   { h := sha256.Sum256([]byte(s)); return hex.EncodeToString(h[:]) }
func same(a, b string) bool  { return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1 }
func now() int64             { return time.Now().Unix() }

func randomHex() string {
	b := make([]byte, 32)
	rand.Read(b)
	return hex.EncodeToString(b)
}

func blobPath(dir, id string) string { return filepath.Join(dir, "s", id) }

type device struct {
	ID        string `json:"id"`
	TokenHash string `json:"tokenHash"`
	Admin     bool   `json:"admin"`
	Created   int64  `json:"created"`
	LastSeen  int64  `json:"lastSeen"`
}

type invite struct {
	NonceHash string `json:"nonceHash"`
	Expires   int64  `json:"expires"`
}

// blob is the file on disk: the ciphertext (a v3 envelope, see web/core.js) plus what only the server sees.
// A group without a recovery hash is legacy (made before devices existed): TOKEN still opens it, and claim enrolls into it.
type blob struct {
	LastSynced int64    `json:"lastSynced"` // unix seconds, at most an hour stale
	Members    []device `json:"members,omitempty"`
	Invites    []invite `json:"invites,omitempty"`
	Recovery   string   `json:"recovery,omitempty"` // sha256 of the client's stretched password
	Ciphertext []byte   `json:"ciphertext,omitempty"`
}

func (bl *blob) legacy() bool { return bl.Recovery == "" }

func (bl *blob) member(id string) *device {
	for i := range bl.Members {
		if bl.Members[i].ID == id {
			return &bl.Members[i]
		}
	}
	return nil
}

// enroll adds (or re-keys) device id and returns its new token.
func (bl *blob) enroll(id string, admin bool) string {
	token := randomHex()
	bl.Members = slices.DeleteFunc(bl.Members, func(d device) bool { return d.ID == id })
	bl.Members = append(bl.Members, device{ID: id, TokenHash: hash(token), Admin: admin, Created: now(), LastSeen: now()})
	return token
}

func readBlob(dir, id string) (blob, error) {
	var bl blob
	f, err := os.Open(blobPath(dir, id))
	if err != nil {
		return bl, err
	}
	defer f.Close()
	zr, err := gzip.NewReader(f)
	if err != nil {
		return bl, err
	}
	b, err := io.ReadAll(zr)
	if err != nil {
		return bl, err
	}
	meta, ct, _ := bytes.Cut(b, []byte("\n")) // json escapes newlines, so the first one ends the metadata
	bl.Ciphertext = nil
	if err = json.Unmarshal(meta, &bl); err == nil {
		bl.Ciphertext = ct
	}
	return bl, err
}

func writeBlob(dir, id string, bl blob) error {
	p := blobPath(dir, id)
	if err := os.MkdirAll(filepath.Dir(p), 0o700); err != nil {
		return err
	}
	bl.Invites = slices.DeleteFunc(bl.Invites, func(i invite) bool { return i.Expires < now() })
	ct := bl.Ciphertext
	bl.Ciphertext = nil
	meta, _ := json.Marshal(bl)
	var buf bytes.Buffer
	zw := gzip.NewWriter(&buf)
	zw.Write(meta)
	zw.Write([]byte("\n"))
	zw.Write(ct)
	zw.Close()
	if err := os.WriteFile(p+".tmp", buf.Bytes(), 0o600); err != nil {
		return err
	}
	return os.Rename(p+".tmp", p) // atomic replace
}

// limits counts events per key ("fail ip", "create ip", "recovery id") in memory; the counts reset every hour.
type limits struct {
	mu sync.Mutex
	n  map[string]int
	at int64
}

func (l *limits) count(key string, add int) int {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.n == nil || now()-l.at >= hour {
		l.n, l.at = map[string]int{}, now()
	}
	l.n[key] += add
	return l.n[key]
}

// clientIP: the server runs behind a reverse proxy on localhost (compose.yml), which appends the real address to X-Forwarded-For.
func clientIP(r *http.Request) string {
	if f := r.Header.Get("X-Forwarded-For"); f != "" {
		parts := strings.Split(f, ",")
		return strings.TrimSpace(parts[len(parts)-1])
	}
	host, _, _ := net.SplitHostPort(r.RemoteAddr)
	return host
}

func bearer(r *http.Request) string {
	return strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
}

// doc is the readable part of a v3 envelope, for stats and cleanup.
type doc struct {
	Counters []struct {
		Deleted bool `json:"deleted"`
	} `json:"counters"`
	Events []struct {
		At      int64 `json:"at"` // ms
		Deleted bool  `json:"deleted"`
	} `json:"events"`
}

func readable(ciphertext []byte) (d doc) {
	var env struct {
		V   int    `json:"v"`
		Doc string `json:"doc"`
	}
	if json.Unmarshal(ciphertext, &env) == nil && env.V == 3 {
		json.Unmarshal([]byte(env.Doc), &d)
	}
	return d // empty for anything but v3
}

func (d doc) live() (n int) {
	for _, c := range d.Counters {
		if !c.Deleted {
			n++
		}
	}
	return n
}

// cleanup deletes groups not synced for a year, or for half a year with fewer than 3 live counters.
func cleanup(dir string) {
	blobMu.Lock()
	defer blobMu.Unlock()
	files, _ := os.ReadDir(filepath.Join(dir, "s"))
	for _, f := range files {
		if !idRe.MatchString(f.Name()) {
			continue
		}
		bl, err := readBlob(dir, f.Name())
		if err != nil {
			continue
		}
		idle := now() - bl.LastSynced
		if idle > 365*day || idle > 180*day && readable(bl.Ciphertext).live() < 3 {
			os.Remove(blobPath(dir, f.Name()))
		}
	}
}

type stats struct {
	Groups, Devices, Counters, Resets7, Resets30 int
}

func collect(dir string) (s stats) {
	blobMu.Lock()
	defer blobMu.Unlock()
	files, _ := os.ReadDir(filepath.Join(dir, "s"))
	ms := now() * 1000
	for _, f := range files {
		bl, err := readBlob(dir, f.Name())
		if !idRe.MatchString(f.Name()) || err != nil {
			continue
		}
		d := readable(bl.Ciphertext)
		s.Groups++
		s.Devices += len(bl.Members)
		s.Counters += d.live()
		for _, e := range d.Events {
			if !e.Deleted && e.At > ms-7*day*1000 {
				s.Resets7++
			}
			if !e.Deleted && e.At > ms-30*day*1000 {
				s.Resets30++
			}
		}
	}
	return s
}

func countGroups(dir string) int {
	files, _ := os.ReadDir(filepath.Join(dir, "s"))
	return len(files)
}

type server struct {
	dir, token                    string
	createsPerIP, maxGr, maxAdmin int
	lim                           limits
}

// canAdmin says whether device dev may become admin of group id: at most maxAdmin groups each (id itself doesn't count).
// ponytail: reads every group file, fine for the rare admin grant; an index once groups move to a database.
func (s *server) canAdmin(dev, id string) error {
	files, _ := os.ReadDir(filepath.Join(s.dir, "s"))
	n := 0
	for _, f := range files {
		if f.Name() == id || !idRe.MatchString(f.Name()) {
			continue
		}
		if bl, err := readBlob(s.dir, f.Name()); err == nil {
			if d := bl.member(dev); d != nil && d.Admin {
				n++
			}
		}
	}
	if n >= s.maxAdmin {
		return errAdminLimit
	}
	return nil
}

type httpErr struct {
	code int
	msg  string
}

func (e httpErr) Error() string { return e.msg }

var (
	errAuth      = httpErr{401, "unauthorized"}
	errForbidden = httpErr{403, "admins only"}
	errNotFound  = httpErr{404, "not found"}
	errConflict  = httpErr{409, "conflict"}
	errBusy      = httpErr{429, "too many attempts, try again later"}
	errBadBody   = httpErr{400, "bad body"}
	// 402: a cap that a paid plan could lift, and a status the clients can tell apart from a wrong password
	errAdminLimit = httpErr{402, "admin group limit reached"}
)

// req is what a group request carries in its JSON body (each endpoint reads the fields it needs).
type req struct {
	Ciphertext []byte `json:"ciphertext,omitempty"`
	DeviceID   string `json:"deviceId"`
	Recovery   string `json:"recovery"`
	Nonce      string `json:"nonce"`
}

// caller is who sent a request: an enrolled device, or (legacy groups only) a holder of TOKEN.
type caller struct {
	dev    *device
	legacy bool
}

func (s *server) auth(r *http.Request, bl *blob) (caller, error) {
	t := bearer(r)
	if t != "" {
		h := hash(t)
		for i := range bl.Members {
			if same(bl.Members[i].TokenHash, h) {
				return caller{dev: &bl.Members[i]}, nil
			}
		}
		if s.token != "" && bl.legacy() && same(t, s.token) {
			return caller{legacy: true}, nil
		}
	}
	s.lim.count("fail "+clientIP(r), 1)
	return caller{}, errAuth
}

func (s *server) admin(r *http.Request, bl *blob) (*device, error) {
	c, err := s.auth(r, bl)
	if err != nil {
		return nil, err
	}
	if c.dev == nil || !c.dev.Admin {
		return nil, errForbidden
	}
	return c.dev, nil
}

// checkRecovery compares a stretched password, at most maxRecovery wrong ones per group per hour.
func (s *server) checkRecovery(r *http.Request, id string, bl *blob, recovery string) error {
	if s.lim.count("recovery "+id, 0) >= maxRecovery {
		return errBusy
	}
	if bl.legacy() || recovery == "" || !same(hash(recovery), bl.Recovery) {
		s.lim.count("recovery "+id, 1)
		s.lim.count("fail "+clientIP(r), 1)
		return httpErr{401, "wrong recovery password"}
	}
	return nil
}

// group runs f on group id under the lock. f says whether to save; a group that ends with no device and isn't legacy is deleted.
// The returned value, if any, is sent as JSON.
func (s *server) group(w http.ResponseWriter, r *http.Request, f func(id string, bl *blob, in req) (any, bool, error)) {
	id := r.PathValue("id")
	if !idRe.MatchString(id) {
		http.Error(w, "bad id", 400)
		return
	}
	if s.lim.count("fail "+clientIP(r), 0) >= maxFails {
		http.Error(w, errBusy.msg, errBusy.code)
		return
	}
	var in req
	if r.Method != http.MethodGet {
		if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 10<<20)).Decode(&in); err != nil {
			http.Error(w, errBadBody.msg, 400)
			return
		}
	}
	blobMu.Lock()
	out, err := func() (any, error) {
		defer blobMu.Unlock()
		bl, err := readBlob(s.dir, id)
		create := r.Method == http.MethodPut && r.Header.Get("If-Match") == ""
		switch {
		case errors.Is(err, os.ErrNotExist) && create:
		case errors.Is(err, os.ErrNotExist):
			return nil, errNotFound
		case err != nil:
			return nil, err
		case create:
			return nil, errConflict
		}
		out, save, err := f(id, &bl, in)
		if err != nil || !save {
			return out, err
		}
		if len(bl.Members) == 0 && !bl.legacy() {
			return out, os.Remove(blobPath(s.dir, id))
		}
		return out, writeBlob(s.dir, id, bl)
	}()
	var he httpErr
	switch {
	case errors.As(err, &he):
		http.Error(w, he.msg, he.code)
	case err != nil:
		http.Error(w, err.Error(), 500)
	case out != nil:
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(out)
	}
}

// seen stamps the caller and the group, but only once an hour, so reads rarely write to disk.
func seen(bl *blob, c caller) bool {
	save := now()-bl.LastSynced > hour
	if save {
		bl.LastSynced = now()
	}
	if c.dev != nil && now()-c.dev.LastSeen > hour {
		c.dev.LastSeen, save = now(), true
	}
	return save
}

func (s *server) routes() *http.ServeMux {
	mux := http.NewServeMux()
	static := func(path, typ string, body []byte) {
		mux.HandleFunc("GET "+path, func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", typ)
			w.Write(body)
		})
	}
	site, _ := fs.Sub(web, "web")
	mux.Handle("GET /", http.FileServerFS(site))
	static("/manifest.webmanifest", "application/manifest+json", []byte(manifest))
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })
	mux.HandleFunc("GET /limits", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]int{"maxAdminGroups": s.maxAdmin})
	})
	mux.HandleFunc("GET /stats", func(w http.ResponseWriter, r *http.Request) {
		if s.token == "" || !same(bearer(r), s.token) {
			http.Error(w, errAuth.msg, 401)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(collect(s.dir))
	})
	handle := func(pattern string, f func(id string, bl *blob, in req, r *http.Request) (any, bool, error)) {
		mux.HandleFunc(pattern, func(w http.ResponseWriter, r *http.Request) {
			s.group(w, r, func(id string, bl *blob, in req) (any, bool, error) { return f(id, bl, in, r) })
		})
	}
	handle("GET /s/{id}", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		c, err := s.auth(r, bl)
		if err != nil {
			return nil, false, err
		}
		return map[string]any{"etag": etagOf(bl.Ciphertext), "ciphertext": bl.Ciphertext}, seen(bl, c), nil // []byte is base64 in JSON
	})
	handle("PUT /s/{id}", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		if len(in.Ciphertext) == 0 {
			return nil, false, errBadBody
		}
		if bl.Ciphertext == nil { // create: open to anyone, within the caps
			if !devRe.MatchString(in.DeviceID) || in.Recovery == "" {
				return nil, false, errBadBody
			}
			if err := s.canAdmin(in.DeviceID, id); err != nil {
				return nil, false, err
			}
			if s.lim.count("create "+clientIP(r), 1) > s.createsPerIP || countGroups(s.dir) >= s.maxGr {
				return nil, false, errBusy
			}
			bl.Recovery, bl.Ciphertext, bl.LastSynced = hash(in.Recovery), in.Ciphertext, now()
			token := bl.enroll(in.DeviceID, true)
			return map[string]string{"etag": etagOf(in.Ciphertext), "token": token}, true, nil
		}
		c, err := s.auth(r, bl)
		if err != nil {
			return nil, false, err
		}
		if r.Header.Get("If-Match") != etagOf(bl.Ciphertext) {
			return nil, false, errConflict
		}
		bl.Ciphertext = in.Ciphertext
		seen(bl, c)
		return map[string]string{"etag": etagOf(in.Ciphertext)}, true, nil
	})
	handle("POST /s/{id}/invite", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		if _, err := s.admin(r, bl); err != nil {
			return nil, false, err
		}
		nonce := randomHex()
		bl.Invites = append(bl.Invites, invite{hash(nonce), now() + inviteTTL})
		return map[string]string{"nonce": nonce}, true, nil
	})
	handle("POST /s/{id}/enroll", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		if !devRe.MatchString(in.DeviceID) {
			return nil, false, errBadBody
		}
		if in.Nonce == "" {
			if err := s.checkRecovery(r, id, bl, in.Recovery); err != nil {
				return nil, false, err
			}
			if err := s.canAdmin(in.DeviceID, id); err != nil {
				return nil, false, err
			}
			return map[string]string{"token": bl.enroll(in.DeviceID, true)}, true, nil
		}
		h := hash(in.Nonce)
		i := slices.IndexFunc(bl.Invites, func(i invite) bool { return same(i.NonceHash, h) && i.Expires >= now() })
		if i < 0 {
			s.lim.count("fail "+clientIP(r), 1)
			return nil, false, httpErr{401, "invite used or expired"}
		}
		bl.Invites = slices.Delete(bl.Invites, i, i+1)
		return map[string]string{"token": bl.enroll(in.DeviceID, false)}, true, nil
	})
	handle("POST /s/{id}/promote", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		c, err := s.auth(r, bl)
		if err != nil || c.dev == nil {
			return nil, false, errAuth
		}
		if err := s.checkRecovery(r, id, bl, in.Recovery); err != nil {
			return nil, false, err
		}
		if err := s.canAdmin(c.dev.ID, id); err != nil {
			return nil, false, err
		}
		c.dev.Admin = true
		return nil, true, nil
	})
	// Sets the recovery password: an admin changes it; on a legacy group, the first claimed device sets it and becomes admin.
	handle("POST /s/{id}/recovery", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		c, err := s.auth(r, bl)
		if err != nil || c.dev == nil {
			return nil, false, errAuth
		}
		if !c.dev.Admin && !bl.legacy() {
			return nil, false, errForbidden
		}
		if in.Recovery == "" {
			return nil, false, errBadBody
		}
		if !c.dev.Admin { // claiming a legacy group; an admin changing the password keeps what it has
			if err := s.canAdmin(c.dev.ID, id); err != nil {
				return nil, false, err
			}
		}
		bl.Recovery, c.dev.Admin = hash(in.Recovery), true
		return nil, true, nil
	})
	handle("POST /s/{id}/revoke", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		if _, err := s.admin(r, bl); err != nil {
			return nil, false, err
		}
		if bl.member(in.DeviceID) == nil {
			return nil, false, errNotFound
		}
		bl.Members = slices.DeleteFunc(bl.Members, func(d device) bool { return d.ID == in.DeviceID })
		return nil, true, nil
	})
	handle("GET /s/{id}/devices", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		if _, err := s.admin(r, bl); err != nil {
			return nil, false, err
		}
		type pub struct {
			ID       string `json:"id"`
			Admin    bool   `json:"admin"`
			Created  int64  `json:"created"`
			LastSeen int64  `json:"lastSeen"`
		}
		out := []pub{}
		for _, d := range bl.Members {
			out = append(out, pub{d.ID, d.Admin, d.Created, d.LastSeen})
		}
		return out, false, nil
	})
	handle("POST /s/{id}/leave", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		c, err := s.auth(r, bl)
		if err != nil || c.dev == nil {
			return nil, false, err
		}
		me := c.dev.ID
		bl.Members = slices.DeleteFunc(bl.Members, func(d device) bool { return d.ID == me })
		return nil, true, nil
	})
	handle("POST /s/{id}/claim", func(id string, bl *blob, in req, r *http.Request) (any, bool, error) {
		if !bl.legacy() || s.token == "" || !same(bearer(r), s.token) {
			s.lim.count("fail "+clientIP(r), 1)
			return nil, false, errAuth
		}
		if !devRe.MatchString(in.DeviceID) {
			return nil, false, errBadBody
		}
		return map[string]string{"token": bl.enroll(in.DeviceID, false)}, true, nil
	})
	return mux
}

// cors lets a web app served by another server sync here. Any origin is fine: requests carry a bearer token, never cookies.
func cors(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		if r.Method == http.MethodOptions {
			w.Header().Set("Access-Control-Allow-Methods", "GET, PUT, POST")
			w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, If-Match")
			w.Header().Set("Access-Control-Max-Age", "86400")
			w.Header().Set("Access-Control-Allow-Private-Network", "true") // a public page syncing with a tailnet or LAN server
			w.WriteHeader(http.StatusNoContent)
			return
		}
		h.ServeHTTP(w, r)
	})
}

func main() {
	dir := env("DATA_DIR", "/data")
	s := &server{dir: dir, token: os.Getenv("TOKEN"), createsPerIP: envInt("CREATES_PER_IP_HOUR", 10), maxGr: envInt("MAX_GROUPS", 10000),
		maxAdmin: envInt("MAX_ADMIN_GROUPS", 10)}
	go func() {
		for {
			cleanup(dir)
			time.Sleep(24 * time.Hour)
		}
	}()
	addr := env("ADDR", ":8080")
	log.Printf("listening on %s, data %s", addr, dir)
	srv := &http.Server{Addr: addr, Handler: cors(s.routes()), ReadHeaderTimeout: 10 * time.Second, ReadTimeout: 30 * time.Second,
		WriteTimeout: 30 * time.Second, IdleTimeout: 2 * time.Minute} // drop slow or stuck clients
	log.Fatal(srv.ListenAndServe())
}
