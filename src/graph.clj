(ns graph
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn >def | ? =>]]
    [ubergraph.core :as u]))

(comment
  (def g (u/graph [1 2] [2 3] {3 [4] 5 [6 7]} 7 8 9))

  (u/viz-graph g))
