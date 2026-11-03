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
	if err := addDevices(dir, id, 1); err != nil { // 1 (creator) + 1
		t.Fatal(err)
	}
	if bl, _ := readBlob(dir, id); bl.LastSynced == 0 || bl.LastJoined == 0 || bl.LastLeft != 0 {
		t.Error("join and sync must be stamped, leave not yet")
	}
	addDevices(dir, id, -1)
	if bl, _ := readBlob(dir, id); bl.LastLeft == 0 {
		t.Error("leave must be stamped")
	}
	if _, _, err := getBlob(dir, id); err != nil {
		t.Error("group with a device left must stay")
	}
	addDevices(dir, id, -1)
	if _, _, err := getBlob(dir, id); !errors.Is(err, os.ErrNotExist) {
		t.Error("group with no devices must be deleted")
	}
	if err := addDevices(dir, id, 1); !errors.Is(err, os.ErrNotExist) {
		t.Error("join of a missing group must be ErrNotExist")
	}
}
