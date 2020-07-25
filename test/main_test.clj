(ns main-test
  ; ns must be .*-test
  (:require
    [clojure.test :refer :all]
    [main :as m]))

(deftest test1
  (is (= 1 1)))




;(testing "Arithmetic"
;  (testing "with positive integers"
;    (is (= 44 (+ 2 2)))
;    (is (= 77 (+ 3 4)))))
(testing "abc"
  (is (= 1 1)))

(def code1 "(re-frame/reg-event-db\n  ::iphone-rewrite-desc-twitter-call-oembed-all-cards\n  [check-spec-interceptor]\n  (fn [db [_ _]]\n    (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: \")\n    (let [iphone-state (:iphone-state-and-view db)\n          nperpage     (:cards-per-page iphone-state)\n          pagenum      (:page-num iphone-state)\n          startingcard (* pagenum nperpage)]\n      ; only call oembed on the cards on the curent page\n      (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: iphone state: \" iphone-state)\n      (doseq [n (range startingcard\n                       (+ startingcard nperpage))]\n        (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: n: \" n)\n        (re-frame/dispatch [::rewrite-desc-twitter-call-oembed n])))\n    db))")

(testing "re-frame-export"
  (let [retval (m/find-dispatches (read-string code1) [])]
    (is (= (first retval)
           :main-test/rewrite-desc-twitter-call-oembed))))
;(testing "update-state"
;  (let [state {:row 20 :col 20}]
;    (is (= (m/update-state \j state)
;           {:row 21 :col 20}))
;    (is (= (m/update-state \k state)
;           {:row 19 :col 20}))
;    (is (= (m/update-state \h state)
;           {:row 20 :col 19}))
;    (is (= (m/update-state \l state)
;           {:row 20 :col 21}))))
