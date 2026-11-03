package main

import (
	"bytes"
	"compress/gzip"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

const gid = "0123456789abcdef0123456789abcdef"

type tc struct {
	t   *testing.T
	s   *server
	srv *httptest.Server
}

func newTC(t *testing.T) *tc {
	s := &server{dir: t.TempDir(), token: "legacy", createsPerIP: 3, maxGr: 100, maxAdmin: 3}
	srv := httptest.NewServer(cors(gz(s.handler())))
	t.Cleanup(srv.Close)
	return &tc{t, s, srv}
}

// do sends a request and returns the status and the decoded JSON answer.
func (c *tc) do(method, path, token, ifMatch string, body any) (int, map[string]any) {
	var rd *strings.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		rd = strings.NewReader(string(b))
	} else {
		rd = strings.NewReader("")
	}
	r, _ := http.NewRequest(method, c.srv.URL+path, rd)
	if token != "" {
		r.Header.Set("Authorization", "Bearer "+token)
	}
	if ifMatch != "" {
		r.Header.Set("If-Match", ifMatch)
	}
	res, err := http.DefaultClient.Do(r)
	if err != nil {
		c.t.Fatal(err)
	}
	defer res.Body.Close()
	out := map[string]any{}
	json.NewDecoder(res.Body).Decode(&out)
	return res.StatusCode, out
}

func (c *tc) want(code int, got int, what string) {
	c.t.Helper()
	if got != code {
		c.t.Errorf("%s: got %d, want %d", what, got, code)
	}
}

// create makes group gid with Alice's device as admin and returns her token and the etag.
func (c *tc) create() (string, string) {
	code, out := c.do("PUT", "/s/"+gid, "", "", map[string]any{"ciphertext": []byte("one"), "deviceId": "alice", "recovery": "pw"})
	c.want(200, code, "create")
	return out["token"].(string), out["etag"].(string)
}

func (c *tc) invite(admin string) string {
	code, out := c.do("POST", "/s/"+gid+"/invite", admin, "", map[string]any{})
	c.want(200, code, "invite")
	return out["nonce"].(string)
}

func (c *tc) enroll(dev string, body map[string]any) (int, string) {
	body["deviceId"] = dev
	code, out := c.do("POST", "/s/"+gid+"/enroll", "", "", body)
	tok, _ := out["token"].(string)
	return code, tok
}

func TestSyncAndRoles(t *testing.T) {
	c := newTC(t)
	alice, e1 := c.create()
	code, _ := c.do("PUT", "/s/"+gid, "", "", map[string]any{"ciphertext": []byte("x"), "deviceId": "eve", "recovery": "x"})
	c.want(409, code, "create over an existing group")
	code, out := c.do("GET", "/s/"+gid, alice, "", nil)
	c.want(200, code, "get")
	if out["etag"] != e1 {
		t.Error("etag")
	}
	c.want(401, first(c.do("GET", "/s/"+gid, "", "", nil)), "get without token")
	c.want(401, first(c.do("GET", "/s/"+gid, "legacy", "", nil)), "TOKEN can't open a new group")
	c.want(409, first(c.do("PUT", "/s/"+gid, alice, "stale", map[string]any{"ciphertext": []byte("x")})), "stale etag")
	c.want(200, first(c.do("PUT", "/s/"+gid, alice, e1, map[string]any{"ciphertext": []byte("two")})), "update")

	// Bob joins with an invite; the nonce is single use.
	n := c.invite(alice)
	code, bob := c.enroll("bob", map[string]any{"nonce": n})
	c.want(200, code, "enroll")
	c.want(401, first(c.enroll("eve", map[string]any{"nonce": n})), "spent nonce")
	c.want(200, first(c.do("GET", "/s/"+gid, bob, "", nil)), "bob syncs")

	// Bob is a member: no invite, revoke, list or changing the password.
	c.want(403, first(c.do("POST", "/s/"+gid+"/invite", bob, "", map[string]any{})), "member invite")
	c.want(403, first(c.do("POST", "/s/"+gid+"/revoke", bob, "", map[string]any{"deviceId": "alice"})), "member revoke")
	c.want(403, first(c.do("GET", "/s/"+gid+"/devices", bob, "", nil)), "member list")
	c.want(403, first(c.do("POST", "/s/"+gid+"/recovery", bob, "", map[string]any{"recovery": "mine"})), "member sets password")

	// Alice lists and revokes Bob.
	r, _ := http.NewRequest("GET", c.srv.URL+"/s/"+gid+"/devices", nil)
	r.Header.Set("Authorization", "Bearer "+alice)
	res, _ := http.DefaultClient.Do(r)
	var list []device
	json.NewDecoder(res.Body).Decode(&list)
	if len(list) != 2 || !list[0].Admin || list[1].Admin || list[0].TokenHash != "" {
		t.Errorf("device list: %+v", list)
	}
	c.want(200, first(c.do("POST", "/s/"+gid+"/revoke", alice, "", map[string]any{"deviceId": "bob"})), "revoke")
	c.want(401, first(c.do("GET", "/s/"+gid, bob, "", nil)), "revoked device")
}

func TestExpiredInvite(t *testing.T) {
	c := newTC(t)
	alice, _ := c.create()
	n := c.invite(alice)
	bl, _ := readBlob(c.s.dir, gid)
	bl.Invites[0].Expires = now() - 1
	var buf bytes.Buffer // by hand: writeBlob would prune the invite, and this tests the enroll check
	zw := gzip.NewWriter(&buf)
	meta, _ := json.Marshal(blob{LastSynced: bl.LastSynced, Members: bl.Members, Invites: bl.Invites, Recovery: bl.Recovery})
	zw.Write(append(append(meta, '\n'), bl.Ciphertext...))
	zw.Close()
	os.WriteFile(blobPath(c.s.dir, gid), buf.Bytes(), 0o600)
	c.want(401, first(c.enroll("bob", map[string]any{"nonce": n})), "expired nonce")
}

func TestRecovery(t *testing.T) {
	c := newTC(t)
	alice, _ := c.create()
	// Alice's new phone joins with the backup link and the password, then revokes the lost one.
	c.want(401, first(c.enroll("alice2", map[string]any{"recovery": "wrong"})), "wrong password")
	code, alice2 := c.enroll("alice2", map[string]any{"recovery": "pw"})
	c.want(200, code, "recovery enroll")
	c.want(200, first(c.do("POST", "/s/"+gid+"/revoke", alice2, "", map[string]any{"deviceId": "alice"})), "new phone revokes old")
	c.want(401, first(c.do("GET", "/s/"+gid, alice, "", nil)), "old phone is out")

	// A member promotes itself with the password.
	_, bob := c.enroll("bob", map[string]any{"nonce": c.invite(alice2)})
	c.want(401, first(c.do("POST", "/s/"+gid+"/promote", bob, "", map[string]any{"recovery": "nope"})), "wrong promote")
	c.want(200, first(c.do("POST", "/s/"+gid+"/promote", bob, "", map[string]any{"recovery": "pw"})), "promote")
	c.want(200, first(c.do("POST", "/s/"+gid+"/invite", bob, "", map[string]any{})), "promoted can invite")

	// Five wrong passwords lock recovery for the hour, even the right one.
	for range 5 {
		c.enroll("eve", map[string]any{"recovery": "guess"})
	}
	c.want(429, first(c.enroll("eve", map[string]any{"recovery": "pw"})), "attempt limit")
}

func TestLegacyClaim(t *testing.T) {
	c := newTC(t)
	writeBlob(c.s.dir, gid, blob{Ciphertext: []byte("old"), LastSynced: now()}) // made before devices existed
	c.want(200, first(c.do("GET", "/s/"+gid, "legacy", "", nil)), "TOKEN opens a legacy group")
	code, out := c.do("POST", "/s/"+gid+"/claim", "legacy", "", map[string]any{"deviceId": "alice"})
	c.want(200, code, "claim")
	alice := out["token"].(string)
	_, out = c.do("POST", "/s/"+gid+"/claim", "legacy", "", map[string]any{"deviceId": "bob"})
	bob := out["token"].(string)
	c.want(403, first(c.do("POST", "/s/"+gid+"/invite", alice, "", map[string]any{})), "claimed devices are members")
	c.want(200, first(c.do("POST", "/s/"+gid+"/recovery", alice, "", map[string]any{"recovery": "pw"})), "first sets password")
	c.want(200, first(c.do("POST", "/s/"+gid+"/invite", alice, "", map[string]any{})), "and is admin")
	c.want(403, first(c.do("POST", "/s/"+gid+"/recovery", bob, "", map[string]any{"recovery": "bob"})), "second can't")
	c.want(401, first(c.do("POST", "/s/"+gid+"/claim", "legacy", "", map[string]any{"deviceId": "eve"})), "claim closed")
	c.want(401, first(c.do("GET", "/s/"+gid, "legacy", "", nil)), "TOKEN closed")
	c.want(200, first(c.do("GET", "/s/"+gid, bob, "", nil)), "bob still syncs")
}

func TestLeaveDeletes(t *testing.T) {
	c := newTC(t)
	alice, _ := c.create()
	_, bob := c.enroll("bob", map[string]any{"nonce": c.invite(alice)})
	c.want(200, first(c.do("POST", "/s/"+gid+"/leave", bob, "", map[string]any{})), "leave")
	c.want(200, first(c.do("GET", "/s/"+gid, alice, "", nil)), "group stays")
	c.do("POST", "/s/"+gid+"/leave", alice, "", map[string]any{})
	if _, err := os.Stat(blobPath(c.s.dir, gid)); !os.IsNotExist(err) {
		t.Error("group with no device left must be deleted")
	}
}

func TestCaps(t *testing.T) {
	c := newTC(t)
	for i := range 4 {
		id := strings.Repeat(string("abcd"[i]), 32)
		code, _ := c.do("PUT", "/s/"+id, "", "", map[string]any{"ciphertext": []byte("x"), "deviceId": "d" + id[:1], "recovery": "pw"})
		c.want(map[bool]int{true: 200, false: 429}[i < 3], code, "creates per IP")
	}
	c.want(400, first(c.do("GET", "/s/ABC", "", "", nil)), "bad id")
	r, _ := http.NewRequest("OPTIONS", c.srv.URL+"/s/"+gid, nil)
	res, _ := http.DefaultClient.Do(r)
	if res.StatusCode != 204 || !strings.Contains(res.Header.Get("Access-Control-Allow-Headers"), "If-Match") {
		t.Errorf("CORS preflight: %d %v", res.StatusCode, res.Header)
	}
	for range maxFails {
		c.do("GET", "/s/"+strings.Repeat("a", 32), "bad", "", nil)
	}
	c.want(429, first(c.do("GET", "/s/"+strings.Repeat("a", 32), "bad", "", nil)), "backoff after failures")
}

// Alice may be admin of one group: creating, recovering, promoting into or claiming a second is refused with 402.
func TestAdminLimit(t *testing.T) {
	c := newTC(t)
	c.s.maxAdmin = 1
	if _, out := c.do("GET", "/limits", "", "", nil); out["maxAdminGroups"] != 1.0 {
		t.Errorf("limits: %v", out)
	}
	alice, _ := c.create()
	other := strings.Repeat("b", 32)
	put := func(dev string) int {
		return first(c.do("PUT", "/s/"+other, "", "", map[string]any{"ciphertext": []byte("x"), "deviceId": dev, "recovery": "pw"}))
	}
	c.want(402, put("alice"), "create a second group")
	code, alice := c.enroll("alice", map[string]any{"recovery": "pw"}) // re-keys her token
	c.want(200, code, "re-enroll in her own group")
	c.want(200, first(c.do("POST", "/s/"+gid+"/recovery", alice, "", map[string]any{"recovery": "pw2"})), "admin changes password")
	c.want(200, put("bob"), "bob creates his group")
	c.want(402, first(c.do("POST", "/s/"+other+"/enroll", "", "", map[string]any{"deviceId": "alice", "recovery": "pw"})), "recovery enroll elsewhere")
	code, out := c.do("POST", "/s/"+gid+"/invite", alice, "", map[string]any{})
	c.want(200, code, "alice invites bob")
	_, bob := c.enroll("bob", map[string]any{"nonce": out["nonce"]})
	c.want(402, first(c.do("POST", "/s/"+gid+"/promote", bob, "", map[string]any{"recovery": "pw2"})), "promote over the limit")
	legacy := strings.Repeat("c", 32)
	writeBlob(c.s.dir, legacy, blob{Ciphertext: []byte("old"), LastSynced: now()})
	_, out = c.do("POST", "/s/"+legacy+"/claim", "legacy", "", map[string]any{"deviceId": "alice"})
	c.want(402, first(c.do("POST", "/s/"+legacy+"/recovery", out["token"].(string), "", map[string]any{"recovery": "x"})), "legacy claim over the limit")
}

func TestCleanupAndStats(t *testing.T) {
	c := newTC(t)
	v4 := func(live int, at int64) []byte {
		var cs []string
		for i := range live {
			es := ""
			if i == 0 {
				es = `{"at":` + itoa(at) + `,"deleted":false}`
			}
			cs = append(cs, `{"deleted":false,"events":[`+es+`]}`)
		}
		d, _ := json.Marshal(`{"counters":[` + strings.Join(cs, ",") + `]}`)
		return []byte(`{"v":4,"doc":` + string(d) + `,"mac":"x"}`)
	}
	ago := func(days int64) int64 { return now() - days*day }
	ids := map[string]blob{
		strings.Repeat("1", 32): {LastSynced: ago(400), Ciphertext: v4(5, 0)},                                                           // a year idle: deleted
		strings.Repeat("2", 32): {LastSynced: ago(200), Ciphertext: v4(1, 0)},                                                           // half a year, few counters: deleted
		strings.Repeat("3", 32): {LastSynced: ago(200), Ciphertext: v4(3, 0)},                                                           // half a year, in use: kept
		strings.Repeat("4", 32): {LastSynced: ago(1), Ciphertext: v4(2, (now()-day)*1000), Recovery: "h", Members: []device{{ID: "a"}}}, // kept
	}
	for id, bl := range ids {
		writeBlob(c.s.dir, id, bl)
	}
	cleanup(c.s.dir)
	for id, kept := range map[string]bool{strings.Repeat("1", 32): false, strings.Repeat("2", 32): false, strings.Repeat("3", 32): true, strings.Repeat("4", 32): true} {
		if _, err := os.Stat(blobPath(c.s.dir, id)); (err == nil) != kept {
			t.Errorf("%s kept=%v", id[:1], err == nil)
		}
	}
	c.want(401, first(c.do("GET", "/stats", "", "", nil)), "stats need TOKEN")
	code, out := c.do("GET", "/stats", "legacy", "", nil)
	c.want(200, code, "stats")
	if out["Groups"] != 2.0 || out["Devices"] != 1.0 || out["Counters"] != 5.0 || out["Resets7"] != 1.0 || out["Resets30"] != 1.0 {
		t.Errorf("stats: %v", out)
	}
}

func first(code int, _ any) int { return code }

func itoa(n int64) string { b, _ := json.Marshal(n); return string(b) }

func TestBlobRoundTrip(t *testing.T) {
	dir := t.TempDir()
	in := blob{LastSynced: 5, Recovery: "h", Ciphertext: []byte("a\nb\xff\xfe\n\x00")}
	if err := writeBlob(dir, gid, in); err != nil {
		t.Fatal(err)
	}
	out, err := readBlob(dir, gid)
	if err != nil || out.LastSynced != 5 || out.Recovery != "h" || string(out.Ciphertext) != string(in.Ciphertext) {
		t.Fatalf("got %+v, %v", out, err)
	}
}

func gzBody(b []byte) *bytes.Reader {
	var buf bytes.Buffer
	zw := gzip.NewWriter(&buf)
	zw.Write(b)
	zw.Close()
	return bytes.NewReader(buf.Bytes())
}

func (c *tc) raw(method, path, token string, body *bytes.Reader, hdr map[string]string) *http.Response {
	var r *http.Request
	if body != nil {
		r, _ = http.NewRequest(method, c.srv.URL+path, body)
	} else {
		r, _ = http.NewRequest(method, c.srv.URL+path, nil)
	}
	if token != "" {
		r.Header.Set("Authorization", "Bearer "+token)
	}
	for k, v := range hdr {
		r.Header.Set(k, v)
	}
	res, err := http.DefaultClient.Do(r)
	if err != nil {
		c.t.Fatal(err)
	}
	res.Body.Close()
	return res
}

func TestGzip(t *testing.T) {
	c := newTC(t)
	enc := map[string]string{"Content-Encoding": "gzip"}
	b0, _ := json.Marshal(map[string]any{"ciphertext": []byte("one"), "deviceId": "alice", "recovery": "pw"})
	r0, _ := http.NewRequest("PUT", c.srv.URL+"/s/"+gid, gzBody(b0))
	r0.Header.Set("Content-Encoding", "gzip")
	res0, err := http.DefaultClient.Do(r0)
	if err != nil {
		t.Fatal(err)
	}
	var o map[string]any
	json.NewDecoder(res0.Body).Decode(&o)
	res0.Body.Close()
	c.want(200, res0.StatusCode, "gzipped create")
	tok, etag := o["token"].(string), o["etag"].(string)
	b, _ := json.Marshal(map[string]any{"ciphertext": []byte("two")})
	c.want(200, c.raw("PUT", "/s/"+gid, tok, gzBody(b), map[string]string{"Content-Encoding": "gzip", "If-Match": etag}).StatusCode, "gzipped update")

	c.want(400, c.raw("PUT", "/s/"+gid, tok, bytes.NewReader([]byte("not gzip")), enc).StatusCode, "bad gzip header")
	if code := c.raw("PUT", "/s/"+gid, tok, gzBody(make([]byte, 11<<20)), enc).StatusCode; code == 200 {
		t.Error("zip bomb accepted")
	}

	get := func(ae string) (*http.Response, []byte) {
		r, _ := http.NewRequest("GET", c.srv.URL+"/s/"+gid, nil)
		r.Header.Set("Authorization", "Bearer "+tok)
		r.Header.Set("Accept-Encoding", ae) // set explicitly so Go doesn't decompress for us
		res, err := http.DefaultClient.Do(r)
		if err != nil {
			t.Fatal(err)
		}
		defer res.Body.Close()
		d, _ := io.ReadAll(res.Body)
		return res, d
	}
	plainRes, plain := get("identity")
	zres, zb := get("gzip")
	if plainRes.Header.Get("Content-Encoding") != "" {
		t.Error("plain GET was compressed")
	}
	if zres.Header.Get("Content-Encoding") != "gzip" {
		t.Fatal("GET not gzipped")
	}
	zr, err := gzip.NewReader(bytes.NewReader(zb))
	if err != nil {
		t.Fatal(err)
	}
	if un, _ := io.ReadAll(zr); !bytes.Equal(un, plain) {
		t.Error("gunzipped body differs from plain body")
	}

	r, _ := http.NewRequest("OPTIONS", c.srv.URL+"/s/"+gid, nil)
	r.Header.Set("Accept-Encoding", "gzip")
	res, _ := http.DefaultClient.Do(r)
	res.Body.Close()
	if res.StatusCode != 204 || res.Header.Get("Content-Encoding") != "" {
		t.Errorf("preflight: %d, encoding %q", res.StatusCode, res.Header.Get("Content-Encoding"))
	}
	if !strings.Contains(res.Header.Get("Access-Control-Allow-Headers"), "Content-Encoding") {
		t.Error("preflight doesn't allow Content-Encoding")
	}

	// Handlers that call WriteHeader before Write (static files, errors) must still say they're gzipped.
	for path, want := range map[string]string{"/": "<html", "/s/" + gid + "/devices": "unauthorized"} {
		r, _ := http.NewRequest("GET", c.srv.URL+path, nil)
		r.Header.Set("Accept-Encoding", "gzip")
		r.Header.Set("Authorization", "Bearer nope")
		res, err := http.DefaultClient.Do(r)
		if err != nil {
			t.Fatal(err)
		}
		zr, err := gzip.NewReader(res.Body)
		if res.Header.Get("Content-Encoding") != "gzip" || err != nil {
			t.Fatalf("%s: encoding %q, %v", path, res.Header.Get("Content-Encoding"), err)
		}
		if b, _ := io.ReadAll(zr); !strings.Contains(strings.ToLower(string(b)), want) {
			t.Errorf("%s: %d %q", path, res.StatusCode, b)
		}
		res.Body.Close()
	}
}

// The website is at /, the web app and API under /app/, and the API also at the root for older clients.
func TestPaths(t *testing.T) {
	c := newTC(t)
	for path, want := range map[string]string{"/": "<!doctype html>", "/it/": `lang="it"`, "/app/": `id="list"`, "/app/limits": "maxAdminGroups", "/limits": "maxAdminGroups"} {
		res, err := http.Get(c.srv.URL + path)
		if err != nil {
			t.Fatal(err)
		}
		b, _ := io.ReadAll(res.Body)
		res.Body.Close()
		if res.StatusCode != 200 || !strings.Contains(string(b), want) {
			t.Errorf("%s: %d, missing %q", path, res.StatusCode, want)
		}
	}
	code, _ := c.do("PUT", "/app/s/"+gid, "", "", map[string]any{"ciphertext": "YQ==", "deviceId": "00000000-0000-4000-8000-000000000001", "recovery": "r"})
	c.want(200, code, "create under /app")
	code, _ = c.do("GET", "/s/"+gid, "", "", nil)
	c.want(401, code, "same group at the root needs a token")
}
