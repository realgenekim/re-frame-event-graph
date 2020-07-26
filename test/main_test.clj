(ns main-test
  ; ns must be .*-test
  (:require
    [clojure.test :refer :all]
    [main :as m]
    [graph :as g]))

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
      (is (= [[:test1]]
             (m/find-dispatches (read-string c1)))))

    (let [c1 "(re-frame/reg-event-db _ _ (fn[x]  (do (re-frame/dispatch [:test1]))))"]
      (is (= [[:test1]]
             (m/find-dispatches (read-string c1))))))

  (testing "rf3"
    (let [c1 "(re-frame/reg-event-fx _ _ (fn[x] {:db 123 :dispatch [:test1]}))"]
      (is (= [[:test1]]
             (m/extract-fx (read-string c1))))))

  (testing "rf4"
    (let [c1 "(re-frame/reg-event-fx _ _ (fn[x] {:db 123 :dispatch-n [[:test2][:test1]]}))"]
      (is (= [[:test2] [:test1]]
             (m/extract-fx (read-string c1)))))))



(def code1 "(re-frame/reg-event-db\n  ::iphone-rewrite-desc-twitter-call-oembed-all-cards\n  [check-spec-interceptor]\n  (fn [db [_ _]]\n    (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: \")\n    (let [iphone-state (:iphone-state-and-view db)\n          nperpage     (:cards-per-page iphone-state)\n          pagenum      (:page-num iphone-state)\n          startingcard (* pagenum nperpage)]\n      ; only call oembed on the cards on the curent page\n      (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: iphone state: \" iphone-state)\n      (doseq [n (range startingcard\n                       (+ startingcard nperpage))]\n        (println \"::iphone-rewrite-desc-twitter-call-oembed-all-cards: n: \" n)\n        (re-frame/dispatch [::rewrite-desc-twitter-call-oembed n])))\n    db))")
(def code2 "(re-frame/reg-event-fx\n  ::rewrite-desc-twitter-call-oembed\n  [check-spec-interceptor]\n  (fn [{:keys [db]} [_ cardpos]]        ; usually current card (:card-position db)\n                                        ; currstate, and new state\n    (let [cards  (:materialized-cards db)\n          _      (println \"::rewrite-desc-twitter-call-oembed: \" cardpos)\n          ; only if there is a current card\n          ; I'd love to refactor this, but everytime I try to pull it out of let-binding,\n          ; I get \"object not seqable\"\n          retval (if (pos? (count cards))\n                   ;(dbg\n                   (let [c      (get cards cardpos)\n                         desc   (:desc c)\n                         tweets (io/get-tweet-urls desc)\n                         _ (println \"    tweets: \" tweets)]\n                     (if tweets\n                       ; db will get rewritten in ::handle-twitter-oembed\n                       (let [t-chan (io/rewrite-tweets-in-description desc tweets)]\n                         (go\n                           (let [oembed-retval (<! t-chan)]\n                             ;_             (cljs.pprint/pprint oembed-retval)]\n                             (re-frame/dispatch [::callback-handle-twitter-oembed\n                                                 {:retval oembed-retval\n                                                  :pos cardpos}])))))))])))")
(def code3 "(re-frame/reg-event-db\n  ::callback-archive-card\n  [check-spec-interceptor]\n  (fn [db [_ card]]\n    (let [newdb (dbh/remove-card-from-raw-local-list! (:id card) db)]\n      (println \":::trello-workflow.events/callback-archive-card\")\n      (io/display-notification \"Card archived\" :success)\n      ; XXX change this\n      ;(re-frame/dispatch [::reset-card-state])\n      (re-frame/dispatch [::generate-materialized-cards])\n      (re-frame/dispatch [::load-card-comments-attachments])\n      (re-frame/dispatch [::load-list-card-counts])\n      newdb)))")
(def code4 "(re-frame/reg-event-fx\n  ::move-card-success\n  [check-spec-interceptor]\n  (fn [{:keys [db]} [_ card-id]]\n    ; card has been moved, so that means current card and current list is out of date\n    ; and we need to change the side-pane view back to show lists\n    ;\n    ; side effect: changes focus of cursor, depending on state\n    ; (focus-searchbox (:left-pane-view db))\n    (println \"::move-card-success: card-id: \" card-id)\n    ; TODO: put list name here\n    (io/display-notification \"Card moved\" :success)\n    (let [newdb          (dbh/remove-card-from-raw-local-list! card-id db)\n          savedboard     (:move-save-old-board-id db)\n          currboard      (:selected-board db)\n          left-pane-view (:left-pane-view db)]\n      ;(re-frame/dispatch-sync [::generate-materialized-cards])\n      {:db       (assoc newdb\n                   ; set new state of left-pane\n                   ; if mode isn't filtering cards, go back to select-list mode\n                   ; otherwise, stay in filtering cards mode\n                   :left-pane-view (if (not= left-pane-view :leftpane-filter-cards)\n                                     :leftpane-select-list\n                                     :leftpane-filter-cards)\n                   :selected-board (if savedboard\n                                     ; when we moved across boards, replace current board (which is currently move target board)\n                                     ; with savedboard\n                                     savedboard\n                                     ; otherwise, maintain current board\n                                     currboard)\n                   :move-save-old-board-id nil)\n       :dispatch-n [[::reset-card]\n                    [::load-card-comments-attachments]\n                    [::generate-materialized-cards]]})))")

(deftest rf-export
  (testing "code1"
    (let [retval (m/find-dispatches (read-string code1))]
      (is (= :user/rewrite-desc-twitter-call-oembed
             (ffirst retval)))))

  (testing "code2"
    (let [retval (m/find-dispatches (read-string code2))]
      (is (= :user/callback-handle-twitter-oembed
             (ffirst retval)))))

  (testing "code3"
    (let [retval (m/find-dispatches (read-string code3))]
      (is (= [[:user/generate-materialized-cards]
              [:user/load-card-comments-attachments]
              [:user/load-list-card-counts]]
             retval))

      (is (= (m/extract-fx (read-string code3))
             nil))))
  (testing "code4"
    (let [retval (m/find-dispatches (read-string code4))]
      (is (= [[:user/reset-card]
              [:user/load-card-comments-attachments]
              [:user/generate-materialized-cards]]
             retval))

      (is (= [[:user/reset-card]
              [:user/load-card-comments-attachments]
              [:user/generate-materialized-cards]]
             (m/extract-fx (read-string code4)))))))

(deftest graph
  (testing "re2"
    (is (= [:a]
           (g/remove-empty-2nd [:a '()]))))
  (testing "ce"
    (is (= [[:a :b] [:b :c] [:c :d]]
           (g/connect-edges [[:a] [:b] [:c] [:d]]))))
  (testing "connect events"
    (is (= '([:a :b])
           (g/connect-events [[:a '([:b])]])))
    (is (= '([:a :b]
             [:a :c])
           (g/connect-events [[:a '([:b] [:c])]])))))



