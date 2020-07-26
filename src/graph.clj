(ns graph
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn >def | ? =>]]
    [ubergraph.core :as u]
    [main :as m]))

(>defn remove-empty-2nd
  [v] [vector? => vector?]
  (let [f (first v)
        r (second v)]
    (if (or (nil? r)
            (empty? r))
      [f]
      v)))

(comment
  (def g (u/graph [1 2] [2 3] {3 [4] 5 [6 7]} 7 8 9))

  (u/viz-graph g)

  (def events (->> (m/gen-events)
                   (map remove-empty-2nd)
                   (take 10)))

  (def e (apply u/graph events))

  (def e (apply u/digraph
                  (->> events
                       (map first)
                       (map (fn [x]
                              [x {:shape :box}])))))

  (u/viz-graph e {:layout :dot})

  (u/viz-graph e {:layout :dot
                  :save {:filename "save.dot"
                         :format :dot}}))
