package main

import "testing"

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
	for _, c := range merge(out, s).Counters {
		if c.Label == "old" {
			t.Error("older counter must not win")
		}
	}
}
