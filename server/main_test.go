package main

import (
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
