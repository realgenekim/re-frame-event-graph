(ns graph
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn >def | ? =>]]
    [ubergraph.core :as u]
    [main :as m]))



(>defn connect-edges
  "[:a :b :c] => [:a :b] [:b :c]"
  [v] [sequential? => sequential?]
  (let [v1 (map first v)]
    (partition 2 (flatten
                   (interleave
                     v1
                     (drop 1 v1))))))

(>defn connect-events
  [events] [sequential? => sequential?]
  (mapcat identity
          (for [es events]
            (do
              ;(println "es: " es)
              (let [[src dests] es]
                ;(println "    src: " src)
                ;(println "    dests: " dests)
                ;(println "    f-dests: " (filter sequential? dests))
                (for [d (filter sequential? dests)]
                  [src (first d)]))))))

(comment
  (connect-events
    (->> events
         (remove #(not= 2 (count %))))))



(comment
  (def g (u/graph [1 2] [2 3] {3 [4] 5 [6 7]} 7 8 9))

  (u/viz-graph g)

  (def events (->> (m/gen-events)
                   (map m/remove-empty-2nd)))
                   ;(take 10)))

  (def e (apply u/graph events))

  ; lining things up

  (def e (apply u/digraph
                  (->> events
                       (map first)
                       (map (fn [x]
                              [x {:shape :box}])))))

  (def e2 (apply u/add-directed-edges e
                 (->> events
                      connect-edges
                      (map (fn [x]
                             (conj (apply vector x) {:style :invis}))))))

  (def e3 (apply u/add-directed-edges e2
                 (->> events
                      (remove #(not= 2 (count %)))
                      connect-events)))

  (u/viz-graph e3 {:layout :dot})
  (u/viz-graph e3 {:layout :fdp})
  (u/viz-graph e3 {:layout :circo})

  (u/viz-graph e {:layout :dot
                  :save {:filename "save.dot"
                         :format :dot}}))
