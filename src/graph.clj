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


(defn make-box
 [x]
 [x {:shape :box}])

(defn create-graph
  [events]
  (let [nodes (->> (mapcat identity events)
                   (remove #(= '() %))
                   flatten
                   (filter keyword?)
                   (map make-box))

        g1 (apply u/digraph nodes)

        edges (->> events
                   (remove #(not= 2 (count %)))
                   connect-events)

        g2 (apply u/add-directed-edges g1 edges)]

    (u/viz-graph g2 {:layout :dot :rankdir :LR})
    g2))




(comment
  (def g (u/graph [1 2] [2 3] {3 [4] 5 [6 7]} 7 8 9))

  (u/viz-graph g)

  (def events (->> (m/gen-events m/infile)))
                   ;(take 40)))

  (create-graph events)
  (def save-graph (create-graph events))

  (def nodes (->> (mapcat identity events)
                  (remove #(= '() %))
                  flatten
                  (filter keyword?)
                  (map make-box)))

  (def g1 (apply u/digraph nodes))

  ;(def e2 (apply u/add-directed-edges e
  ;               (->> events
  ;                    connect-edges
  ;                    (map (fn [x]
  ;                           (conj (apply vector x) {:style :invis}))))))

  (def edges (->> events
                  (remove #(not= 2 (count %)))
                  connect-events))

  (def g2 (apply u/add-directed-edges g1 edges))


  (u/viz-graph g2 {:layout :dot :rankdir :LR})

  (u/viz-graph e3 {:layout :fdp})
  (u/viz-graph e3 {:layout :circo})

  (u/viz-graph save-graph {:layout :dot :rankdir :LR
                           :save {:filename "save.dot"
                                  :format :dot}})

  (u/viz-graph save-graph {:layout :dot :rankdir :LR
                           :save {:filename "save.png"
                                  :format :png}}))
