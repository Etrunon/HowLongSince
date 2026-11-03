package main

import (
	"errors"
	"os"
	"reflect"
	"testing"
)

func TestMerge(t *testing.T) {
	s := State{
		Counters: []Counter{{ID: "a", Label: "old", UpdatedAt: 1}},
		Events:   []Event{{ID: "e1", CounterID: "a", Deleted: true}},
	}
	in := State{
		Counters: []Counter{{ID: "a", Label: "new", UpdatedAt: 2}, {ID: "b", Label: "b", UpdatedAt: 1}},
		Events:   []Event{{ID: "e1", CounterID: "a"}, {ID: "e2", CounterID: "b"}},
	}
	out := merge(s, in)
	if len(out.Counters) != 2 || len(out.Events) != 2 {
		t.Fatal(out)
	}
	for _, c := range out.Counters {
		if c.ID == "a" && c.Label != "new" {
			t.Error("newer counter should win")
		}
	}
	for _, e := range out.Events {
		if e.ID == "e1" && !e.Deleted {
			t.Error("deletion should stick")
		}
	}
	if !reflect.DeepEqual(merge(out, State{}), out) {
		t.Error("merging nothing must leave the state equal (skips the write)")
	}
	for _, c := range merge(out, s).Counters {
		if c.Label == "old" {
			t.Error("older counter must not win")
		}
	}
}

func TestPrune(t *testing.T) {
	s := State{
		Counters: []Counter{{ID: "old", Deleted: true, UpdatedAt: 1}, {ID: "new", Deleted: true, UpdatedAt: 10}, {ID: "live", UpdatedAt: 1}},
		Events:   []Event{{ID: "e1", CounterID: "old"}, {ID: "e2", CounterID: "new"}, {ID: "e3", CounterID: "live"}},
	}
	out := prune(s, 5)
	if len(out.Counters) != 2 || len(out.Events) != 2 {
		t.Fatal(out)
	}
	for _, e := range out.Events {
		if e.CounterID == "old" {
			t.Error("events of a pruned counter must go")
		}
	}
}

func TestMergeKeepsNote(t *testing.T) {
	s := State{Events: []Event{{ID: "e", CounterID: "a", Note: "hi"}}}
	out := merge(s, State{Events: []Event{{ID: "e", CounterID: "a"}}})
	if out.Events[0].Note != "hi" {
		t.Error("note lost")
	}
}

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
