(ns main-test
  ; ns must be .*-test
  (:require
    [clojure.test :refer :all]
    [main :as m]))

(deftest test1
  (is (= 1 1))
  (is (= 1 1)))

(deftest db-fx
  (is (= (m/db-or-fx? (read-string "(re-frame/reg-event-db _ _ _)"))
         :db))
  (is (= (m/db-or-fx? (read-string "(re-frame/reg-event-fx _ _ {:db 123 :dispatch [123]})"))
         :fx)))

(deftest rf1
  (testing "rf1"
    (let [c1 "(re-frame/reg-event-db _ _ (fn[x]  (re-frame/dispatch [:test1])))"]
      (is (= (m/find-dispatches (read-string c1))
             [:test1])))
    (let [c1 "(re-frame/reg-event-db _ _ (fn[x]  (do (re-frame/dispatch [:test1]))))"]
      (is (= (m/find-dispatches (read-string c1))
             [:test1])))
    (let [c1 "(re-frame/reg-event-fx _ _ (fn[x] {:db 123 :dispatch [:test1]}))"]
      (is (= (m/extract-fx (read-string c1))
             [[:test1]])))
    (let [c1 "(re-frame/reg-event-fx _ _ (fn[x] {:db 123 :dispatch-n [[:test2][:test1]]}))"]
      (is (= (m/extract-fx (read-string c1))
             [[:test2] [:test1]])))))


(def code1 "(re-frame/reg-event-db\n  ::iphone-rewrite-desc-twitter-call-oembed-all-cards\n  [check-spec-interceptor]\n  (fn [db [_ _]]\n    (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: \")\n    (let [iphone-state (:iphone-state-and-view db)\n          nperpage     (:cards-per-page iphone-state)\n          pagenum      (:page-num iphone-state)\n          startingcard (* pagenum nperpage)]\n      ; only call oembed on the cards on the curent page\n      (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: iphone state: \" iphone-state)\n      (doseq [n (range startingcard\n                       (+ startingcard nperpage))]\n        (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: n: \" n)\n        (re-frame/dispatch [::rewrite-desc-twitter-call-oembed n])))\n    db))")
(def code2 "(re-frame/reg-event-fx\n  ::rewrite-desc-twitter-call-oembed\n  [check-spec-interceptor]\n  (fn [{:keys [db]} [_ cardpos]]        ; usually current card (:card-position db)\n                                        ; currstate, and new state\n    (let [cards  (:materialized-cards db)\n          _      (println \"::rewrite-desc-twitter-call-oembed: \" cardpos)\n          ; only if there is a current card\n          ; I'd love to refactor this, but everytime I try to pull it out of let-binding,\n          ; I get \"object not seqable\"\n          retval (if (pos? (count cards))\n                   ;(dbg\n                   (let [c      (get cards cardpos)\n                         desc   (:desc c)\n                         tweets (io/get-tweet-urls desc)\n                         _ (println \"    tweets: \" tweets)]\n                     (if tweets\n                       ; db will get rewritten in ::handle-twitter-oembed\n                       (let [t-chan (io/rewrite-tweets-in-description desc tweets)]\n                         (go\n                           (let [oembed-retval (<! t-chan)]\n                             ;_             (cljs.pprint/pprint oembed-retval)]\n                             (re-frame/dispatch [::callback-handle-twitter-oembed\n                                                 {:retval oembed-retval\n                                                  :pos cardpos}])))))))])))")


(deftest rf-export
  (testing "re-frame-export"
    (let [retval (m/find-dispatches (read-string code1) [])]
      (is (= (first retval)
             :user/rewrite-desc-twitter-call-oembed)))
    (let [retval (m/find-dispatches (read-string code2) [])]
      (is (= (first retval)
             :user/callback-handle-twitter-oembed)))))

