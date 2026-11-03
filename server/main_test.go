package main

import (
	"errors"
	"os"
	"testing"
)

func TestBlobs(t *testing.T) {
	dir := t.TempDir()
	id := "0123456789abcdef0123456789abcdef"
	if _, _, err := getBlob(dir, id); !errors.Is(err, os.ErrNotExist) {
		t.Fatal("missing blob must be ErrNotExist", err)
	}
	e1, err := putBlob(dir, id, []byte("one"), "")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := putBlob(dir, id, []byte("x"), ""); !errors.Is(err, errConflict) {
		t.Error("create over an existing blob must conflict")
	}
	if _, err := putBlob(dir, id, []byte("x"), "stale"); !errors.Is(err, errConflict) {
		t.Error("stale etag must conflict")
	}
	e2, err := putBlob(dir, id, []byte("two"), e1)
	if err != nil || e2 == e1 {
		t.Fatal("update with the current etag must work", err)
	}
	if b, e, _ := getBlob(dir, id); string(b) != "two" || e != e2 {
		t.Error("get must return the latest blob and its etag")
	}
	if _, err := putBlob(dir, "ffffffffffffffffffffffffffffffff", []byte("x"), e1); !errors.Is(err, errConflict) {
		t.Error("update of a missing blob must conflict")
	}
	if idRe.MatchString("../etc/passwd") || idRe.MatchString("ABC") {
		t.Error("ids must be 32 lowercase hex chars")
	}
}
