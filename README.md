# Goal

I love re-frame!  It's what has helped me write programs that do what I want them to do, and am able to build upon them for years without them collapsing like a house of cards, as has been my normal experience.

However, I'm finding for one project, I have what feels like a sprawling `events.cljs` file, and a flow of events that's difficult to keep track of and keep in my head.

I wrote this program to help document the re-frame events.  I'm ridiculously proud of myself for writing a Clojure program to read the `events.cljs` file and extract the "dispatch/call graph," pulling out the `re-frame/dispatch` calls and the `reg-event-fx` dispatch maps.  (Holy cow. This is my first visceral experience of awe at the  value of homoiconicity!)

This is a work in process —- no guarantees that it will work with anything besides the included file.  :)  

I was playing around with rendering the call graph with graphviz (did not have good results), or maybe vega arc-diagrams, but I'm finding the text display surprisingly compact and useful.


# Sample Output

Each vector is the name of the event, followed by all the other events that it dispatches.


```
([:graph/load-initial-state-success :graph/server-load-hotkeys]
 [:graph/select-board :graph/save-and-clear-searchbox]
 [:graph/select-board :graph/load-board-lists]
 [:graph/select-board :graph/select-board-next-leftpane-state]
 [:graph/callback-load-board-lists :graph/load-list-cards]
 [:graph/callback-load-board-lists :graph/load-list-card-counts]
 [:graph/load-list-cards :graph/reset-card]
 [:graph/load-list-cards :graph/generate-materialized-cards]
 [:graph/reload-all-lists-and-cards :graph/load-list-cards]
 [:graph/reload-all-lists-and-cards :graph/load-board-lists]
 [:graph/reload-all-lists-and-cards :graph/load-list-card-counts]
 [:graph/change-list-sort-mode :graph/generate-materialized-cards]
 [:graph/generate-materialized-cards :graph/iphone-materialize-view]
 [:graph/load-card-comments-attachments :graph/reset-card]
 [:graph/load-card-comments-attachments :graph/load-card-comments]
 [:graph/load-card-comments-attachments :graph/load-card-attachments]
 [:graph/iphone-materialize-view :graph/iphone-load-card-attachments]
 :
 :
 :

(count *1)
=> 65
```

![Graph rendered in graphviz](save.png)


# What I Learned

Here's what this exercise helped me learn for an SPA which I use for Trello card management, which I've built up over 3 years — it's actually two SPAs, one intended for use in desktop browser (supporting keyboard accelerators) and the other in mobile interface .

- I've learned that I have at least 65 events calling other events,  putatively making the remainder leaf events, which don't dispatch any other events.
- Even before this exercise, it occurred to me that I don't know what the ideal event flow and shape of the call graph should even look like!  What does one look like that allows for re-use, versus what feels like spaghetti right now?  Are there naming schemes for the events to support better organization?
- And what should the naming scheme be for events, to impose an order/organization to it?
    - user-load-lists
    - op-focus-on-comment-box


- This has helped me zero in on where the complexity/messiness is — not surprisingly, it typically is around operations that have multiple asynchronous steps.Like this one:
    -  [:graph/archive-card :graph/next-card]
    -  [:graph/callback-archive-card :graph/generate-materialized-cards]
    -  [:graph/callback-archive-card :graph/load-card-comments-attachments]
    -  [:graph/callback-archive-card :graph/load-list-card-counts]
- I.e., to archive a card, it’s got to advance the view to the next card, call the backed to archive it, upon the callback, pessimistically update the list of card, load the next card contents/comments… I’m wondering aloud how better to do these multi-step sequence of events, where it’s more obvious what the steps are — documenting this way is good, but steps seem currently splattered across too many events, which linkages not obvious enough.

# To Use

Run in the REPL.  Input file is hardcoded in main.clj.

```
(in-ns 'graph)

(def infile "/Users/genekim/src.local/trello-workflow/src/cljs/trello_workflow/events.cljs")

(def events (->> (m/gen-events infile)))

(count graph)
; 110 forms

([:graph/load-initial-state-success :graph/server-load-hotkeys]
 [:graph/select-board :graph/save-and-clear-searchbox]
 [:graph/select-board :graph/load-board-lists]
 :
 :
 :)

(def save-graph (create-graph events))

(u/viz-graph save-graph {:layout :dot :rankdir :LR
                        :save {:filename "save.dot"
                                :format :dot}})

(u/viz-graph save-graph {:layout :dot :rankdir :LR
                        :save {:filename "save.png"
                                :format :png}}))

```

This sample file that works is in the resources directory.

# Known Problems

There are many.

- The way I pull out the `reg-event-fx` dispatch maps is a hack -- I grab either the last s-expr inside the `fn` or the enclosed `let`.  I don't parse it at all, so if there are variables in there, they're not evaluated/expanded, or anything like that.


# TODO and Help I'm Looking For

Pull requests welcome.

- write a proper command-line interface to pass in input file as parameter (so you don't have to write it in a REPL)
- in graph, put back in the event parameters, so you can see the shape of data used as parameters to event
- parse `:http-xhrio` fx


# Install Instructions

To use the graphviz visualation of graph, you need to install it as per: https://graphviz.org/download/

Mac

- brew install graphviz