(ns trello-workflow.events
  #:ghostwheel.core{:trace       0
                    :check       false
                    :ignore-fx   true
                    :num-tests   0
                    :instrument true}
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [ajax.core :as ajax :refer [GET POST]]
    [cljs.core.async :as async :refer [<!]]
    [cljs.spec.alpha :as s]
    ;[debux.cs.core :as d :refer-macros [clog clogn dbg dbgn break]]
    [day8.re-frame.http-fx]
    [ghostwheel.core :as g :refer [>defn >defn- >fdef => | <- ?]]
    [goog.string :as gstring]
    [re-frame.core :as re-frame]
    [trello-workflow.db :as db]
    [trello-workflow.io :as io]
    [trello-workflow.clipboard :as clipboard]
    [trello-workflow.dbhelpers :as dbh]
    [trello-workflow.trello-interfaces :as interfaces]))


(defn check-and-throw
  "Throws an exception if `db` doesn't match the Spec `a-spec`."
  [a-spec db]
  (when-not (s/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {}))))

(def check-spec-interceptor
  (re-frame/after (partial check-and-throw :trello-workflow.db/db)))


;
; default events
;

(re-frame/reg-event-db
  ::initialize-db
  [check-spec-interceptor]
  (fn [_ _]
    db/default-db))

(re-frame/reg-event-db
  ::set-active-panel
  [check-spec-interceptor]
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

;
; load my first initial state
;

(re-frame/reg-event-fx
  ::load-initial-state
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [_    (println "::load-initial-state")
          opts {:command         :init
                :method          :get
                :uri             "/whoami"
                :timeout         30000
                :format          (ajax/json-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::load-initial-state-success]
                :on-failure      [::load-initial-state-success]}]
      (interfaces/invoke interfaces/trello opts)
      {})))




(re-frame/reg-event-fx
  ::load-initial-state-success
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ ret]]                                ; currstate, and new state
    ; returns new card values: replace it in our (::current-list-cards db)
    (println "::load-initial-state-success: " ret)
    (let [username (:username ret)
          fullname (:fullname ret)
          _        (println "::load-initial-state-success: " username " " fullname)]
      (println "::load-initial-state-success: " username, fullname)
      (io/display-notification "got initial state..." :success)
      {:dispatch [::server-load-hotkeys]
       :db (assoc db :session {:username username :fullname fullname})})))

; reminder: assoc operation
;   Clojure> (assoc [1 2 3] 1 5)
;   [1 5 3]

(re-frame/reg-event-db
  ::load-initial-state-failure
  [check-spec-interceptor]
  (fn [db [_ _]]                                            ; currstate, and newtext
    (io/display-notification "Loading initial state FAILED..." :error)
    db))

;
; test notifications
;

(re-frame/reg-event-db
  ::test-notify
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::test-notify")
    (io/display-notification "Test notification!" :success)
    db))

;
; load trello boards names
;


(re-frame/reg-event-db
  ::load-boards
  [check-spec-interceptor]
  (fn [db]
    (interfaces/invoke interfaces/trello {:command :load-boards
                                          :callback ::callback-load-boards})
    ;(io/http-async-get-boards re-frame/dispatch ::callback-load-boards)
    ;(interfaces/invoke interfaces/faketrello {:command :load-boards
    ;                                          :callback ::callback-load-boards})
    (println "::load-boards: fired off go-routine...")
    db))

(re-frame/reg-event-db
  ::callback-load-boards
  [check-spec-interceptor]
  (fn [db [_ boards]]
    (println "::callback-load-boards: type(boards): " (type boards))
    (assoc db :boards boards)))


(re-frame/reg-event-fx
  ::select-board
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ board-id]]                           ; currstate, and new state
    (println "::select-board: " board-id)
    (re-frame/dispatch [::save-and-clear-searchbox])
    ; if moving card, make sure focus is in search box
    (if (= (:left-pane-view db) :leftpane-moving-board)
      (io/focus-searchbox ::focus-searchbox))

    {:dispatch-n (list
                   [::load-board-lists board-id]
                   [::select-board-next-leftpane-state])
     :db       (assoc db :selected-board board-id
                         :board-lists '[])}))


(re-frame/reg-event-fx
  ::select-board-next-leftpane-state
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ board-id]]                           ; currstate, and new state
    (println "::select-board-next-leftpane-state")
    {:db       (assoc db :left-pane-view
                         (condp = (:left-pane-view db)
                           :leftpane-select-board :leftpane-select-list
                           :leftpane-moving-board :leftpane-moving-list))}))

;
; left pane
;
(re-frame/reg-event-db
  ::left-pane-select-board
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::left-pane-select-board: ")
    ;(io/flash-searchbox)
    (assoc db :left-pane-view :leftpane-select-board)))

(re-frame/reg-event-db
  ::left-pane-select-list
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::left-pane-select-list: ")
    ;(io/flash-searchbox)
    (assoc db :left-pane-view :leftpane-select-list)))

(re-frame/reg-event-db
  ::left-pane-filter-cards
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::left-pane-filter-cards: ")
    (io/display-notification "Filtering cards..." :success)
    ;(io/flash-searchbox)
    (assoc db :left-pane-view :leftpane-filter-cards)))

;
; load lists from board
;

(re-frame/reg-event-fx
  ::callback-load-board-lists
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ lists]]                              ; currstate, and new state
    (println ":::trello-workflow.events/callback-load-board-lists: go-routine returned: type(lists): " (type lists))
    (let [ids               (map :id lists)
          first-list        (:id (first lists))
          selected-list     (:selected-list db)
          _                 (println "    selected-list: " selected-list)
          ; is old-selected list still a valid state?
          found             (some #{selected-list} ids)
          new-selected-list (if found
                              ; don't reset :selected-list if it's in the set of returned lists
                              selected-list
                              ; otherwise, reset to first list
                              first-list)]
      (println ":::trello-workflow.events/callback-load-board-lists: first-list: " first-list)
      ; and also set the selected-list to be the first one
      ; and load the cards from that list
      {:dispatch-n (list
                     [::load-list-cards new-selected-list]
                     [::load-list-card-counts])
       :db       (assoc db :board-lists lists)})))

(re-frame/reg-event-db
  ::load-board-lists
  [check-spec-interceptor]
  (fn [db [_ board-id]]
    ;(io/http-async-get-board-lists re-frame/dispatch board-id ::callback-load-board-lists)
    (interfaces/invoke interfaces/trello {:command :load-lists
                                          :board-id board-id
                                          :callback ::callback-load-board-lists})

    (println "::load-board-lists: fired off go-routine: board " board-id)
    ; empty out the cards while we wait for cards to load
    (assoc db :current-list-cards-raw-from-trello [])))

;
; get list card counts
;

(re-frame/reg-event-db
  ::callback-load-list-card-counts
  [check-spec-interceptor]
  (fn [db [_ [k v]]]
    (let [curr-list-count    (:current-board-list-counts db)
          new-list-count-map (merge curr-list-count {k (int v)})]
      (println (gstring/format "::current-board-list-counts: %s: %d" k v))
      (assoc db :current-board-list-counts new-list-count-map))))

(re-frame/reg-event-fx
  ::load-list-card-counts
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [lists (:board-lists db)]
      (println "::load-list-card-counts: count lists: " (count lists))
      ; (println "::load-list-card-counts: lists: " lists)
      ; (println "::load-list-card-counts: type lists: " (type lists))
      ; (lookat-list lists)

      ; OMG.  the FOR loop never worked!  Had to use doseq b/c we needed the side effects!!!
      (doseq [l lists]
        (do
          (println "::load-list-card-counts: firing off list-id: " (:id l))
          ;(io/http-async-get-list-count re-frame/dispatch
          ;                              (:id l) ::callback-load-list-card-counts)
          (interfaces/invoke interfaces/trello {:command :load-list-card-counts
                                                :list-id (:id l)
                                                :callback ::callback-load-list-card-counts})))

      ; this is a no-op
      {:db db})))

;
; load cards
;

; in most cases, we'll want to call ::load-card-comments-attachments instead
(re-frame/reg-event-db
  ::reset-card
  ; reset list state: which means clearing card comments, attachments
  [check-spec-interceptor]
  (fn [db [_ _]]
    (assoc db :current-card-comments '[]
              :current-card-attachments '[]
              :new-comment-text "")))


(re-frame/reg-event-fx
  ::load-list-cards
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ list-id]]
    (interfaces/invoke interfaces/trello {:command :load-list-cards
                                          :list-id list-id
                                          :callback ::callback-load-list-cards})
    (println "::load-list-cards: fired off go-routine: list " list-id)
    {:db       (assoc db :selected-list list-id)
     :dispatch-n [[::reset-card]
                  [::generate-materialized-cards]]}))


(re-frame/reg-event-fx
  ::reload-all-lists-and-cards
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (println "::reload-list")
    {:dispatch-n (list
                   [::load-list-cards (:selected-list db)]
                   [::load-board-lists (:selected-board db)]
                   [::load-list-card-counts])}))



(re-frame/reg-event-db
  ::change-list-sort-mode
  [check-spec-interceptor]
  (fn [db [_ _]]
    (let [mode (:mode-list-sort db)
          newmode (if (= mode :list-sort-normal)
                    :list-sort-by-desc
                    :list-sort-normal)
          msg (str "::change-list-sort-mode: new mode: " newmode)]
      (println msg)
      (io/display-notification msg :success)
      (re-frame/dispatch [::generate-materialized-cards])
      (assoc db :mode-list-sort newmode))))


(re-frame/reg-event-db
  ::generate-materialized-cards
  [check-spec-interceptor]
  (fn [db [_ _]]
    (let [mode      (:mode-list-sort db)
          msg       (str "::generate-materialized-cards: " mode)
          cards     (dbh/materialized-cards (:current-list-cards-raw-from-trello db)
                                            (:mode-list-sort db)
                                            (:searchbox-filter-string db))]
      (println msg)
      (if (= (:active-panel db) :iphone-show-cards)
        (re-frame/dispatch [::iphone-materialize-view]))
      (assoc db :materialized-cards cards))))


(re-frame/reg-event-fx
  ::callback-load-list-cards
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ cards]]
    (println "::callback-load-list-cards: go-routine returned # cards: " (count cards))
    (let [dispatch-list (list [::load-card-comments-attachments]
                              [::generate-materialized-cards])
          dispatch-list (if (= (:active-panel db) :iphone-show-cards)
                          (conj dispatch-list
                                [::iphone-rewrite-desc-twitter-call-oembed-all-cards])
                          dispatch-list)
          _ (println "     dispatch-list: " dispatch-list)]
      ; now that we've loaded the cards, we can fetch the card comments
      ; XXX change this: dispatch [::load-card-metadata]
      {:dispatch-n dispatch-list
       :db         (assoc db :current-list-cards-raw-from-trello cards
                             :materialized-card-position 0)})))

(re-frame/reg-event-fx
  ::load-card-comments-attachments
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (println "::load-card-metadata")
    {:dispatch-n (list
                   [::reset-card]
                   [::load-card-comments]
                   [::load-card-attachments])}))


(re-frame/reg-event-fx
  ::load-card-attachments
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]
    (if (zero? (count (:materialized-cards db)))
      {}
      (let [card (dbh/current-materialized-card db)
            _    (println "::load-card-attachments")]
        (if card
          {:http-xhrio
           {
            :method          :get
            :uri             (gstring/format "/cards/%s/attachments"
                                             (:id card))
            :params          nil
            :timeout         5000
            :format          (ajax/json-request-format)
            :response-format (ajax/json-response-format {:keywords? true})
            :on-success      [::load-card-attachments-success]
            :on-failure      [::load-card-attachments-success]}}
          ; no cards
          nil)))))

(re-frame/reg-event-db
  ::iphone-materialize-view
  [check-spec-interceptor]
  ; adds :curr-page to iphone-state-and-view
  (fn [db [_ ret]]
    (let [oldstate (:iphone-state-and-view db)
          cards    (:materialized-cards db)]
      (println "::iphone-materialize-view: ")
      (if (empty? cards)
        (assoc db :iphone-state-and-view
                  (assoc oldstate :curr-page '[]))
        (let [nperpage       (:cards-per-page oldstate)
              ; merge {:number} into the cards, for the view
              numbered-cards (map
                               #(merge %1 {:number %2})
                               cards
                               (iterate inc 0))
              ;_              (println (count numbered-cards))
              pages          (partition nperpage nperpage nil numbered-cards)
              ;_              (println (count pages))
              ; handle when curr-page needs to be decremented due to archive — i.e., when curr-page disappears
              ; due to archiving card
              curr-page      (nth pages (min (:page-num oldstate)
                                             (dec (count pages))))

              new-state      (assoc oldstate :curr-page curr-page)]
              ;_              (println curr-page)]
          (re-frame/dispatch [::iphone-load-card-attachments])
          (assoc db :iphone-state-and-view new-state))))))

(re-frame/reg-event-db
  ::iphone-load-card-attachments
  [check-spec-interceptor]
  ; adds :curr-page-attachments
  (fn [db [ename _]]
    (println (gstring/format "%s: " ename))
    (let [curr-page   (:curr-page (:iphone-state-and-view db))
          attachments (:iphone-card-attachments db)
          ; the list of cards that don't have attachments
          to-lookup   (->> (for [c curr-page]
                             (if-not (contains? attachments (:id c))
                               c))
                           (remove nil?))]
      (io/http-async-get-attachments to-lookup ::callback-iphone-load-card-attachments)
      db)))

(re-frame/reg-event-db
  ::callback-iphone-load-card-attachments
  [check-spec-interceptor]
  ; adds :curr-page-attachments
  (fn [db [ename [idx card-id retval]]]
    (println (gstring/format "%s: %d: %s" ename idx retval))
    (let [newattach   (assoc (:iphone-card-attachments db)
                        card-id
                        (if (empty? retval)
                          nil
                          retval))]
      (assoc db :iphone-card-attachments newattach))))

(comment
  (def cc (:curr-page (:iphone-state-and-view @re-frame.db/app-db)))
  (def vv (go (doseq [c (take 2 cc)]
                (io/http-sync-get-attachment c)))))


(re-frame/reg-event-db
  ::load-card-attachments-success
  [check-spec-interceptor]
  (fn [db [_ ret]]
    (let [att (:data ret)]
      (println "::load-card-attachments-success: " att)
      (assoc db :current-card-attachments att))))


; select a list — load the lists

(re-frame/reg-event-fx
  ::select-list
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ list-id]]                            ; currstate, and new state
    ; clears input
    (re-frame/dispatch [::save-and-clear-searchbox])
    {:dispatch [::load-list-cards list-id]}))

;
; enter was hit in search box
;

(re-frame/reg-event-fx
  ::select-board-or-list
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ id]]                                 ; currstate, and new state
    (let [left-pane-mode         (:left-pane-view db)
          dispatch               (condp = left-pane-mode
                                   :leftpane-select-board [::select-board id]
                                   :leftpane-select-list [::load-list-cards id]
                                   :leftpane-moving-board [::select-board id]
                                   :leftpane-moving-list [::move-card-to-list [id nil nil]]
                                   :leftpane-create-new-list [::create-list (:searchbox-filter-string db)]
                                   :leftpane-filter-cards [])
          move-board-id          (if (= :leftpane-moving-board left-pane-mode)
                                   id
                                   nil)
          ; if we choose a board, we have to save our current board, so we
          ; can restore it after moving
          move-save-old-board-id (if (= :leftpane-moving-board left-pane-mode)
                                   (:selected-board db)
                                   nil)]
      (println "::select-board-or-list: " dispatch ": mode: " left-pane-mode)
      (io/unfocus-searchbox)
      (when (not= :leftpane-filter-cards left-pane-mode)
        (do
          (println "  dispatch ::save-and-clear-searchbox")
          (re-frame/dispatch [::save-and-clear-searchbox])
          (when dispatch
            (re-frame/dispatch dispatch))))
      {
       :db
       ; reset the searchbox string
                 (let [newdb (assoc db
                               :move-board-id move-board-id
                               :move-save-old-board-id move-save-old-board-id)
                       newdb (if (not= :leftpane-filter-cards left-pane-mode)
                               (assoc newdb :searchbox-filter-string "")
                               newdb)]
                   newdb)})))




; clear searchbox
(re-frame/reg-event-db
  ::save-and-clear-searchbox
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::clear-searchbox")
    (assoc db :searchbox-filter-string "")))

; move-card: select boards
(re-frame/reg-event-db
  ::left-pane-move-select-board
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::left-pane-move-select-board")
    (assoc db :left-pane-view :leftpane-moving-board)))

;
; card comments
;

(re-frame/reg-event-db
  ::load-card-comments
  [check-spec-interceptor]
  (fn [db [_ _]]
    (let [cards (:current-list-cards-raw-from-trello db)]
      (if (pos? (count cards))
        (let [currcard (dbh/current-materialized-card db)]
          (println "::load-card-comments: fired off go-routine: card id: "
                   (:name currcard))
          (if currcard
            (io/http-async-load-card-comments re-frame/dispatch
                                              (:id currcard) ::callback-load-card-comments))))
      db)))

(re-frame/reg-event-fx
  ::callback-load-card-comments
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ comments]]
    (println ":::trello-workflow.events/callback-load-card-comments: go-routine returned # comments: " (count comments))
    {:db
                 (assoc db :current-card-comments comments)
     :dispatch-n (list
                   ;[::rewrite-desc-youtube]
                   [::rewrite-desc-twitter-call-oembed (:materialized-card-position db)])}))

; OMG.  I only do the twitter oembed for the current card
; here's an interface to handle all the cards
;
; only do this for cards in view


(re-frame/reg-event-db
  ::iphone-rewrite-desc-twitter-call-oembed-all-cards
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::iphone-rewrite-desc-twitter-call-oembed-all-cards: ")
    (let [iphone-state (:iphone-state-and-view db)
          nperpage     (:cards-per-page iphone-state)
          pagenum      (:page-num iphone-state)
          startingcard (* pagenum nperpage)]
      ; only call oembed on the cards on the curent page
      (println "::iphone-rewrite-desc-twitter-call-oembed-all-cards: iphone state: " iphone-state)
      (doseq [n (range startingcard
                       (+ startingcard nperpage))]
        (println "::iphone-rewrite-desc-twitter-call-oembed-all-cards: n: " n)
        (re-frame/dispatch [::rewrite-desc-twitter-call-oembed n])))
    db))

(re-frame/reg-event-fx
  ::callback-handle-twitter-oembed
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ args]]                             ; currstate, and new state
    (let [oembed  (:retval args)
          cardpos (:pos args)]
      ;(println "args: " args)
      ;(println "oembed: " oembed)
      (println "oembed: cardpos: " cardpos)
      (s/valid? (s/nilable map?) oembed)
      (let [; loading these three vars should be in a function, because it's so often used
            currcard     (get (:materialized-cards db) cardpos)
            currcardlist (:materialized-cards db)

            newdesc      (io/append-twitter-oembed (:desc currcard) oembed)
            newcard      (merge currcard {:desc-rewritten newdesc})]
        ;_            (println "    newcard: " (cljs.pprint/pprint newcard))]

        {:db
         (assoc db :mode-edit-card-name? false
                   :materialized-cards
                   (assoc currcardlist cardpos newcard))}))))

; called from load-card-comments
(re-frame/reg-event-fx
  ::rewrite-desc-twitter-call-oembed
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ cardpos]]        ; usually current card (:card-position db)
                                        ; currstate, and new state
    (let [cards  (:materialized-cards db)
          _      (println "::rewrite-desc-twitter-call-oembed: " cardpos)
          ; only if there is a current card
          ; I'd love to refactor this, but everytime I try to pull it out of let-binding,
          ; I get "object not seqable"
          retval (if (pos? (count cards))
                   ;(dbg
                   (let [c      (get cards cardpos)
                         desc   (:desc c)
                         tweets (io/get-tweet-urls desc)
                         _ (println "    tweets: " tweets)]
                     (if tweets
                       ; db will get rewritten in ::handle-twitter-oembed
                       (let [t-chan (io/rewrite-tweets-in-description desc tweets)]
                         (go
                           (let [oembed-retval (<! t-chan)]
                             ;_             (cljs.pprint/pprint oembed-retval)]
                             (re-frame/dispatch [::callback-handle-twitter-oembed
                                                 {:retval oembed-retval
                                                  :pos cardpos}])))))))])))
; else add desc-rewritten here
; XXX

;{:db db})))

(re-frame/reg-event-fx
  ::render-twitter-description
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [elem     (.getElementById js/document "app")]
          ; this is the many attempts to get multiple twitter rendering to work
          ; however, simplest is just to render the whole page
          ;elems    (.getElementsByClassName js/document "rendered-card-desc")
          ;cljelems (array-seq elems)
          ;_        (println "::render-twitter-description: # elems: " (count cljelems))]
          ;_     (println "::render-twitter-description: # elems: " elems)]
      ;(println "::render-twitter-description")
      ;(io/display-notification "Rendering tweet" :success)
      (if elem
        (.load js/twttr.widgets elem))
      ;(doseq [e cljelems]
      ;  (println "render tweet in element: " e)
      ;  (.load js/twttr.widgets e))
      {:db db})))


; delete this: this must go in view-cards

;(re-frame/reg-event-db
;  ::rewrite-desc-youtube
;  [check-spec-interceptor]
;  (fn [db [_ _]]
;    (let [currcard     (dbh/get-current-card db)
;          newdesc      (io/transform-all-youtube-urls (:desc currcard))
;          newcard      (merge currcard {:desc-rewritten newdesc})
;          cardpos      (:card-position db)
;          currcardlist (:current-list-cards db)]
;      ;(if (pos? (count cards))
;      ;  (let [c (nth cards (:card-position db))]
;      ;    (println "::load-card-comments: fired off go-routine: card id: "
;      ;             (:name c))
;      ;    (io/http-async-load-card-comments re-frame/dispatch
;      ;                                      (:id c) ::callback-load-card-comments)))
;      (assoc db :edit-card-name? false
;                :current-list-cards
;                (assoc currcardlist cardpos newcard)))))

; helper

; handle all the bookkeeping when changing card position, such as
; blanking out comments
; called when card is archived
(defn set-new-card-position!
  [db newpos callback-v]
  (when (>= (:materialized-card-position db))
    (re-frame/dispatch [callback-v]))
    ;(re-frame/dispatch [::load-card-comments-attachments]))
  (assoc db :materialized-card-position newpos))


(re-frame/reg-event-db
  ::next-card
  [check-spec-interceptor]
  (fn [db [_ _]]
    (let [curpos (:materialized-card-position db)
          size   (count (:current-list-cards-raw-from-trello db))
          newval (io/inc-position curpos size)]
      (println "::next-card: curpos: " curpos)
      (re-frame/dispatch [::scroll-to-top])
      (set-new-card-position! db newval ::load-card-comments-attachments))))

(re-frame/reg-event-db
  ::previous-card
  [check-spec-interceptor]
  (fn [db [_ _]]
    (let [curpos (:materialized-card-position db)
          size   (count (:current-list-cards-raw-from-trello db))
          newval (io/dec-position curpos size)]
      (println "::previous-card: curpos: " curpos)
      (re-frame/dispatch [::scroll-to-top])
      (set-new-card-position! db newval ::load-card-comments-attachments))))

;
(re-frame/reg-event-db
  ::goto-top-card
  [check-spec-interceptor]
  (fn [db [_ _]]
    (re-frame/dispatch [::reset-card])
    (re-frame/dispatch [::load-card-comments-attachments])
    (assoc db :materialized-card-position 0)))

(re-frame/reg-event-db
  ::goto-bottom-card
  [check-spec-interceptor]
  (fn [db [_ _]]
    (re-frame/dispatch [::reset-card])
    (re-frame/dispatch [::load-card-comments-attachments])
    (assoc db :materialized-card-position
              (dec (count (:current-list-cards-raw-from-trello db))))))

(re-frame/reg-event-db
  ::set-card-position
  [check-spec-interceptor]
  (fn [db [_ n]]
    (println "::set-card-position: " n)
    (assoc db :materialized-card-position n)))

;
; archive card
;

(re-frame/reg-event-db
  ::archive-card
  ; potential side effect: ::next-card
  [check-spec-interceptor]
  (fn [db [_ _]]
    (let [card (dbh/current-materialized-card db)]
          ;_ (println card)]
      (io/display-notification "Archiving cards..." :success)
      (if (not= (:active-panel db) :iphone-show-cards)
        (re-frame/dispatch [::next-card]))
      (io/http-async-archive-card card ::callback-archive-card)
      (println "::archive-card: fired off go-routine: card: ")
      db)))


(re-frame/reg-event-db
  ::callback-archive-card
  [check-spec-interceptor]
  (fn [db [_ card]]
    (let [newdb (dbh/remove-card-from-raw-local-list! (:id card) db)]
      (println ":::trello-workflow.events/callback-archive-card")
      (io/display-notification "Card archived" :success)
      ; XXX change this
      ;(re-frame/dispatch [::reset-card-state])
      (re-frame/dispatch [::generate-materialized-cards])
      (re-frame/dispatch [::load-card-comments-attachments])
      (re-frame/dispatch [::load-list-card-counts])
      newdb)))

(re-frame/reg-event-db
  ::archive-card-by-number
  [check-spec-interceptor]
  (fn [db [_ cardpos]]
    (println "::archive-card-by-number: " cardpos)
    ;   [card callback-v]
    (let [currcard     (nth (:materialized-cards db) cardpos)]
      (io/http-async-archive-card currcard
                                  ::callback-archive-card)
      (println "::archive-card: fired off go-routine: card #: "
               (:card-position db))
      db)))




;
; new comment
;


(re-frame/reg-event-db
  ::focus-on-comment-box
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::focus-on-comment-box: ")
    (io/focus-commentbox ::focus-commentbox)
    db))

(re-frame/reg-event-db
  ::new-comment-text
  [check-spec-interceptor]
  (fn [db [_ text]]
    (println "::new-comment-text: " text)
    (assoc db :new-comment-text text)))



;
; post new comments
;


;
; handle submit button
;
(re-frame/reg-event-fx
  ::save-comment-text
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]
    (let [comment (:new-comment-text db)
          card    (nth (:current-list-cards-raw-from-trello db) (:materialized-card-position db))]
      (println "::save-comment-text: SUBMIT button:" comment)
      ;(post-card-comment comment card)
      ;(println "::save-comment-text: fired off go-routine:")
      {
       :http-xhrio
       {
        :method          :post
        :uri             (gstring/format "/cards/%s/comments"
                                         (:id card))
        :params          {:text comment}
        :timeout         5000
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::load-card-comments-attachments]
        :on-failure      [::load-card-comments-attachments]}})))


; filter board/Lists

(re-frame/reg-event-db
  ::left-pane-set-form-text
  [check-spec-interceptor]
  (fn [db [_ text]]
    (println "::left-pane-set-form-text: " text)
    ; if :left-pane-view:leftpane-filter-cards
    ;   then dispatch :generate-filtered-cards-list
    (when (= (:left-pane-view db) :leftpane-filter-cards)
      (do
        (re-frame/dispatch [::generate-materialized-cards])))
    ; skip / when first character
    (if (= "/" text)
      db                                                    ; leave as-is
      (assoc db :searchbox-filter-string text))))

(re-frame/reg-event-db
  ::focus-searchbox
  [check-spec-interceptor]
  (fn [db [_ _]]
    ; side effect: changes focus of cursor, depending on state
    (io/focus-searchbox ::focus-searchbox)
    (io/flash-searchbox)
    (println "::focus-searchbox: ")
    db))

(re-frame/reg-event-db
  ::unfocus-searchbox
  [check-spec-interceptor]
  (fn [db [_ _]]
    ; side effect: changes focus of cursor, depending on state
    (io/unfocus-searchbox)
    (println "::unfocus-searchbox: ")
    (assoc db :searchbox-filter-string "")))

;
; move cards
;

(re-frame/reg-event-db
  ; save the move operation, so we can redo it with "." hotkey
  ::save-move-operation
  [check-spec-interceptor]
  (fn [db [_ [board-id list-id]]]
    (println (gstring/format "::save-move-operation: board=%s, list=%s"
                             board-id list-id))
    (assoc db :move-save-last-operation {:list-id  list-id
                                         :board-id board-id})))

(re-frame/reg-event-fx
  ::start-move-card
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ id]]                                 ; currstate, and new state
    (println "::start-move-card")
    (io/flash-searchbox)
    {:dispatch [::focus-searchbox]
     :db
     ; reset the searchbox string
               (assoc db :left-pane-view :leftpane-moving-list
                         :move-board-id nil)}))

(re-frame/reg-event-fx
  ::move-card-to-list
  ; potential side effect: [::next-card]
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ [dest-list-id board-id pos]]]            ; currstate, and new state
    (s/valid? string? dest-list-id)
    (s/valid? string? board-id)
    (println "::move-card-to-list: pos: " pos board-id dest-list-id)
    (let [curr-card     (dbh/current-materialized-card db)
          card-id       (:id curr-card)
          move-board-id (:move-board-id db)
          params        (merge {:idList dest-list-id}
                               (if move-board-id
                                 ; sometimes board-id comes from saved move-board-id
                                 {:idBoard move-board-id}
                                 ; otherwise, use board-id
                                 {:idBoard board-id})
                               {:pos 0.0})]
                               ;(if pos
                               ;  {:pos pos}
                               ;  {}))]

      ;_             (println "::move-card-to-list: " params)]
      (io/display-notification "Moving card..." :success)
      {
       :http-xhrio
       {
        :method          :put
        :uri             (gstring/format "/cards/%s/move" card-id)
        :params          params
        :timeout         30000
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::move-card-success card-id]
        :on-failure      [::move-card-failure]}

       ; events: next-card and save move operation
       :dispatch-n (into []
                         (remove nil?
                                 [[::save-move-operation [(:idBoard params)
                                                          (:idList params)]]
                                  (if (not= (:active-panel db) :iphone-show-cards)
                                    [::next-card]
                                    nil)]))})))


       ;; also save the move operation, so we can redo it with "." hotkey
       ;:db
       ;(assoc db :move-save-last-operation {:list-id  (:idList params)
       ;                                     :board-id (:idBoard params)})})))




(re-frame/reg-event-fx
  ::move-card-success
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ card-id]]
    ; card has been moved, so that means current card and current list is out of date
    ; and we need to change the side-pane view back to show lists
    ;
    ; side effect: changes focus of cursor, depending on state
    ; (focus-searchbox (:left-pane-view db))
    (println "::move-card-success: card-id: " card-id)
    ; TODO: put list name here
    (io/display-notification "Card moved" :success)
    (let [newdb          (dbh/remove-card-from-raw-local-list! card-id db)
          savedboard     (:move-save-old-board-id db)
          currboard      (:selected-board db)
          left-pane-view (:left-pane-view db)]
      ;(re-frame/dispatch-sync [::generate-materialized-cards])
      {:db       (assoc newdb
                   ; set new state of left-pane
                   ; if mode isn't filtering cards, go back to select-list mode
                   ; otherwise, stay in filtering cards mode
                   :left-pane-view (if (not= left-pane-view :leftpane-filter-cards)
                                     :leftpane-select-list
                                     :leftpane-filter-cards)
                   :selected-board (if savedboard
                                     ; when we moved across boards, replace current board (which is currently move target board)
                                     ; with savedboard
                                     savedboard
                                     ; otherwise, maintain current board
                                     currboard)
                   :move-save-old-board-id nil)
       :dispatch-n [[::reset-card]
                    [::load-card-comments-attachments]
                    [::generate-materialized-cards]]})))


; accidentally put in edit-name-success!!!
; ; if we were moving across boards, go to the saved board
; :selected-board (if savedboard
;                   ; if savedboard isn't nil, replace current board
;                   savedboard
;                   ; otherwise, maintain current board
;                   currboard)
; :selected-board (:move-save-old-board-id db)
; :move-save-old-board-id nil))))


(re-frame/reg-event-db
  ::move-card-failure
  [check-spec-interceptor]
  (fn [db [_ _]]                                            ; currstate, and newtext
    (io/display-notification "Card moved FAILED..." :error)
    db))

;
; move card shortcuts
;   move to first list
;
; {:board-lists
;  [{:id "5a4dbc29e902b88842d9e7a8",
;    :name "Test",}]}


(re-frame/reg-event-fx
  ::move-opt-number-key
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ num]]                                ; currstate, and new state
    (println "::move-opt-number-key " num)
    (let [retval (dbh/handle-control-number-key (js/parseInt num) db)]
      (io/display-notification (gstring/format
                                 "Moving card to list %d %s!" num (:name retval)) :success)
      {:dispatch [::move-card-to-list [
                                       (:id retval)
                                       nil nil]]})))


(re-frame/reg-event-fx
  ::opt-letter-key
  [check-spec-interceptor]
  ; argument could be  "option+a" or "option+shift+z"
  (fn [{:keys [db]} [_ cmd]]                                ; currstate, and new state
    (println "::opt-letter-key: " cmd)
    (let [letter (if (nil? (re-find #"option\+shift\+(.)" cmd))
                   ; lowercase
                   (last (re-find #"option\+(.)" cmd))
                   ; uppercase
                   (clojure.string/upper-case (last (re-find #"option\+shift\+(.)" cmd))))
          _      (println "    " letter)
          hkmap  (:move-target-hotkeys db)
          hk     (get hkmap letter)]
      (println (gstring/format "::opt-letter-key %s: %s" letter hk))
      {:dispatch [::execute-hotkey-move hk]})))

(re-frame/reg-event-fx
  ::move-current-card-to-top
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                ; currstate, and new state
    (println "::move-current-card-to-top:")
    {:dispatch [::move-card-to-top-or-bottom-of-list ["top" (:materialized-card-position db)]]}))

(re-frame/reg-event-fx
  ::move-current-card-to-bottom
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                ; currstate, and new state
    (println "::move-current-card-to-bottom:")
    {:dispatch [::move-card-to-top-or-bottom-of-list ["bottom" (:materialized-card-position db)]]}))


(re-frame/reg-event-fx
  ::move-card-to-top-or-bottom-of-list
  [check-spec-interceptor]
  ; [ "top" / "bottom", cardnum
  (fn [{:keys [db]} [_ [pos num]]]                                ; currstate, and new state
    (println "::move-card-to-top-or-bottom-of-list: " pos num)
    (let [dest-list-id (:selected-list db)
          board-id     nil
          ncards       (count (:current-list-cards-raw-from-trello db))]
      (io/display-notification (gstring/format "Moving card to %s of list" pos)
                               :success)
      ; dest-list-id board-id pos
      {:dispatch-n (conj
                     [[::move-card-to-list [dest-list-id board-id pos]]
                      [::load-list-cards (:selected-list db)]]
                     (if (= pos "top")
                       [::goto-top-card]
                       [::goto-bottom-card]))})))

;
; iphone move card
;

(re-frame/reg-event-fx
  ::iphone-move-card
  ; output: a new iphone-state-and-view: mode
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ num]]                                ; currstate, and new state
    (let [mode             (:iphone-state-and-view db)
          curr-state       (:iphone-mode-moving mode)
          ; :iphone-mode-moving is either
          ;    nil: no card in moving state
          ;    integer?: that card is in moving state
          new-moving-state (if (nil? (:iphone-mode-moving mode))
                             ; if nil, then set to new target
                             num
                             ; if same number, set to nil
                             (if (= curr-state num)
                               nil
                               num))
          new-iphone-mode  (assoc mode :iphone-mode-moving new-moving-state)]
      (println (gstring/format "::iphone-move-card: card %d: new-state: %s"
                       num (str new-moving-state)))
      {:db (assoc db :iphone-state-and-view new-iphone-mode)})))

; manually set cardpos so that we can use ::execute-hotkey-move
(re-frame/reg-event-fx
  ::iphone-set-cardpos-for-move
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ [num letter]]]
    (let [hkmap (:move-target-hotkeys db)
          hk    (get hkmap letter)]
      (println "::iphone-set-cardpos-for-move: " num)
      {:db       (assoc db :materialized-card-position (js/parseInt num))
       :dispatch-n [[::execute-hotkey-move hk]
                    [::iphone-move-card nil]]})))

; manually set cardpos, and then dispatch ::opt-number-key
(re-frame/reg-event-fx
  ::iphone-set-cardpos-for-opt-number-key
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ [num col]]]
    ; must match existing ordering scheme for opt-number-key, which is 1..0 (10)
    ;  [0-9] => [1-0].  retarded, I know..
    ; 0 => 1
    ; 1 => 2
    ; 9 => 10
    (let [opt-key-col (inc (js/parseInt col))]
      (println "::iphone-set-cardpos-for-opt-number-key: card, col = " num col)
      {:db         (assoc db :materialized-card-position (js/parseInt num))
       :dispatch-n [[::move-opt-number-key opt-key-col]
                    [::iphone-move-card nil]]})))

;
; next and previous lists
;


(re-frame/reg-event-db
  ::focus-edit-card-name
  [check-spec-interceptor]
  (fn [db [_ _]]
    ; side effect: changes focus of cursor, depending on state
    (io/focus-edit-card-name ::focus-edit-card-name)
    (println "::focus-edit-card-name")
    db))


(re-frame/reg-event-db
  ::edit-card-text
  [check-spec-interceptor]
  (fn [db [_ _]]
    (println "::edit-card-text")
    (io/display-notification "::edit-card-text!" :success)
    (let [editing?     (not (db :mode-edit-card-name?))
          current-card (dbh/current-materialized-card db)]
      (when editing?
        (re-frame/dispatch [::focus-edit-card-name]))
      (assoc db :mode-edit-card-name? editing?
                :edit-card-text (if (= editing? true)
                                  (:name current-card)
                                  "")))))

(re-frame/reg-event-db
  ::update-edit-card-text
  [check-spec-interceptor]
  (fn [db [_ text]]
    (println "::update-edit-card-text: " text)
    (assoc db :edit-card-text text)))

;
; handle submit button
;

(re-frame/reg-event-fx
  ::save-card-name
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ board-id]]                           ; currstate, and new state
    (let [new-name (:edit-card-text db)
          card-id  (:id (dbh/current-materialized-card db))]
      (println "::save-card-name: SUBMIT button:" card-id " " new-name)
      (io/display-notification "Saving card name..." :success)
      {:http-xhrio
       {
        :method          :put
        :uri             (gstring/format "/cards/%s/change-name" card-id)
        :params          {:value new-name}
        :timeout         5000
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::save-name-success]
        :on-failure      [::save-name-failure]}})))

;
; :card-position 0,
; :current-list-cards

(re-frame/reg-event-fx
  ::save-name-success
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ ret]]                                ; currstate, and new state
    ; returns new card values: replace it in our (::current-list-cards db)
    (let [currcard     (dbh/current-materialized-card db)
          ; retval is missing the :board field, which we need to render card-path correctly
          newcard      (merge (:data ret) {:board (:board currcard)})
          cardpos      (:materialized-card-position db)
          currcardlist (:current-list-cards-raw-from-trello db)]
      (println "::save-name-success: " (str newcard))
      (io/display-notification "Card new name saved..." :success)
      {:db
       (assoc db :mode-edit-card-name? false
                 :current-list-cards-raw-from-trello
                 (assoc currcardlist cardpos newcard))})))

; Clojure> (assoc [1 2 3] 1 5)
; [1 5 3]

(re-frame/reg-event-db
  ::save-name-failure
  [check-spec-interceptor]
  (fn [db [_ _]]                                            ; currstate, and newtext
    (io/display-notification "Card new name save FAILED..." :error)
    db))

(re-frame/reg-event-db
  ::cancel-card-name
  [check-spec-interceptor]
  (fn [db [_ _]]                                            ; currstate, and newtext
    (println "::cancel-card-name")
    (assoc db :mode-edit-card-name? false)))

(re-frame/reg-event-fx
  ::edit-card-key-down
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ args]]                               ; currstate, and new state
    (let [keycode  (:keycode args)
          _        (println "key pressed: " keycode)
          dispatch (cond
                     ; esc key
                     (= keycode 27) (do
                                      println "ESC pressed!"
                                      {:dispatch [::cancel-card-name]})
                     :else nil)
          retval   {:db db}
          ; if there was a :dispatch generated, merge it into retval
          retval   (merge retval dispatch)]
      ;{:dispatch dispatch
      ; :db       db})))
      retval)))

;
; next and previous lists
;

; ">" : next list
(re-frame/reg-event-fx
  ::handle-keyboard-next-prev-list
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ direction]]                          ; currstate, and new state
    (let [list-ids     (map :id (:board-lists db))
          curr-list-id (:selected-list db)
          index        (io/index-of list-ids curr-list-id)
          new-index    (condp = direction
                         :next (io/inc-position index (count (:board-lists db)))
                         :prev (io/dec-position index (count (:board-lists db))))
          new-list-id  (:id (nth (:board-lists db) new-index))
          _            (println (gstring/format "::events/handle-keyboard-next-prev-list: %d -> %d: %s"
                                                index new-index new-list-id))]

      {:dispatch [::load-list-cards new-list-id]
       :db       (assoc db :selected-list new-list-id)})))


;
; set hotkey move targets
;


(re-frame/reg-event-db
  ::focus-hotkeyform
  [check-spec-interceptor]
  (fn [db [_ _]]
    ; side effect: changes focus of cursor, depending on state
    (io/focus-searchbox ::focus-hotkeyform)
    (println "::focus-hotkeyform: ")
    db))

; "M" : move to hotkey
(re-frame/reg-event-fx
  ::handle-keyboard-move-to-hotkey
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [_ (println "::handle-keyboard-set-move-target")]
      {
       :db       (assoc db :left-pane-view :leftpane-move-by-hotkey)
       :dispatch [::focus-hotkeyform]})))

; "S" : assign hotkeys
(re-frame/reg-event-fx
  ::handle-keyboard-set-move-hotkey
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [_ (println "::handle-keyboard-set-move-target")]
      {
       :db       (assoc db :left-pane-view :leftpane-assign-move-hotkeys)
       :dispatch [::focus-hotkeyform]})))

; "D" : delete hotkey
(re-frame/reg-event-fx
  ::handle-keyboard-delete-move-hotkey
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [_ (println "::handle-keyboard-set-move-target")]
      {
       :db       (assoc db :left-pane-view :leftpane-delete-move-hotkeys)
       :dispatch [::focus-hotkeyform]})))

; "G": goto hotkey
(re-frame/reg-event-fx
  ::handle-keyboard-goto-hotkey
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [m (io/get-hotkey-from-form-text db)
          _ (println "::handle-keyboard-goto-hotkey: " m)]
      {
       :db       (assoc db :left-pane-view :leftpane-goto-hotkey)
       :dispatch [::focus-hotkeyform]})))

; TODO: insert retry count for focus!

(re-frame/reg-event-db
  ::update-hotkey-form
  [check-spec-interceptor]
  (fn [db [_ text]]
    (println "::update-hotkey-form " text)
    (assoc db :hotkey-form-text text)))


;
; create new list
;

; "L": create new list
(re-frame/reg-event-fx
  ::left-pane-create-list
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [_ (println "::left-pane-create-list: ")]
      (io/flash-searchbox)
      {
       :db (assoc db :left-pane-view :leftpane-create-new-list)})))

(re-frame/reg-event-db
  ::update-form-new-list
  [check-spec-interceptor]
  (fn [db [_ text]]
    (println "::update-new-list-form" text)
    (assoc db :new-list-form text)))

(re-frame/reg-event-fx
  ::create-list
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ name]]                               ; currstate, and new state
    (let [board-id (:selected-board db)
          params   {:name name
                    :pos  (dbh/compute-pos-for-new-list db)}
          _        (println (gstring/format "::create-list: name %s, board-id %s, params %s"
                                            name, board-id, params))]
      {
       :db (assoc db :left-pane-view :leftpane-select-list)
       :http-xhrio
           {
            :method          :post
            :uri             (gstring/format "/lists/%s" board-id)
            :params          params
            :timeout         5000
            :format          (ajax/json-request-format)
            :response-format (ajax/json-response-format {:keywords? true})
            :on-success      [::create-list-success]
            :on-failure      [::create-list-failure]}})))

;(go (cljs.pprint/pprint (<! (http/post "http://localhost:3000/lists/5a4dbc238e44459958406fd3"
;                                       {:form-params
;                                        {:board-id "5a4dbc238e44459958406fd3"
;                                         :name "NEW"
;                                         :pos "180000"}}))))


(re-frame/reg-event-fx
  ::create-list-success
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ ret]]                                ; currstate, and new state
    ; returns new card values: replace it in our (::current-list-cards db)
    (println "::create-list-success: " ret)
    (io/display-notification "Create list succeeded..." :success)
    {:db       db
     :dispatch [::load-board-lists (:selected-board db)]}))

(re-frame/reg-event-db
  ::create-list-failure
  [check-spec-interceptor]
  (fn [db [_ _]]                                            ; currstate, and newtext
    (io/display-notification "Loading initial state FAILED..." :error)
    db))

;
; hotkeys
;

(re-frame/reg-event-fx
  ::submit-save-hotkey-form
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [text   (:hotkey-form-text db)
          oldval (:move-target-hotkeys db)
          newval {(first text) {:board-id   (:selected-board db)
                                :board-name (dbh/get-board-name (:boards db) (:selected-board db))
                                :list-id    (:selected-list db)
                                :list-name  (dbh/get-list-name (:board-lists db) (:selected-list db))}}
          _      (println (gstring/format "::submit-save-hotkey-form: %s %s" text newval))]


      {:dispatch [::server-save-hotkeys]
       :db (assoc db :move-target-hotkeys (merge oldval newval)
                     :left-pane-view :leftpane-select-list)})))

(re-frame/reg-event-fx
  ::submit-delete-hotkey-form
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [text  (:hotkey-form-text db)
          ; get letter
          k     (first text)
          hkmap (:move-target-hotkeys db)
          ; _       (println "::submit-hotkey-move: map: " hkmap)
          m     (get hkmap k)
          _     (println (gstring/format "::submit-delete-hotkey-form: %s %s" k m))
          newhk (dissoc hkmap k)]
      {:db       (assoc db :move-target-hotkeys newhk)
       :dispatch [::server-save-hotkeys]})))

; move via hotkey
(re-frame/reg-event-fx
  ::submit-hotkey-move
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [m        (io/get-hotkey-from-form-text db)
          _        (println (gstring/format "::submit-hotkey-move: %s" m))
          dispatch (if (= m nil)
                     (do
                       (io/display-notification "no hotkey assigned..." :error)
                       nil)
                     [::execute-hotkey-move m])]
      {:dispatch dispatch})))


; goto hotkey location
(re-frame/reg-event-fx
  ::submit-goto-hotkey-form
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [m (io/get-hotkey-from-form-text db)
          _ (println (gstring/format "::submit-goto-hotkey-form: %s" m))]
      {:dispatch-n [[::select-board (:board-id m)]
                    [::load-list-cards (:list-id m)]]
       :db       (assoc db :left-pane-view :leftpane-select-list
                           :selected-list (:list-id m))})))
;
; TODO: add spec here
;

; https://developers.trello.com/reference/#cardsid-1

(re-frame/reg-event-fx
  ::execute-hotkey-move
  [check-spec-interceptor]
  ; k is letter of keykey
  (fn [{:keys [db]} [_ k]]                                  ; currstate, and new state
    (let [_     (println "::execute-hotkey-move: " k)]
      {
       :dispatch [::move-card-to-list [(:list-id k)
                                       (:board-id k)
                                       ; top pos
                                       "top"]]})))

(re-frame/reg-event-fx
  ::server-save-hotkeys
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]
    (let [hotkeys (:move-target-hotkeys db)]
      (println "::server-save-hotkeys:" hotkeys)
      {
       :http-xhrio
       {
        :method          :put
        :uri             (gstring/format "/move-hotkeys")
        :params          {:hotkeys hotkeys}
        :timeout         30000
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::server-save-hotkeys-success]
        :on-failure      [::server-save-hotkeys-fail]}})))

(re-frame/reg-event-fx
  ::server-save-hotkeys-success
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (io/display-notification "Save hotkeys: SUCCESS" :success)
    {:db db}))

(re-frame/reg-event-fx
  ::server-save-hotkeys-fail
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (io/display-notification "Save hotkeys: FAIL" :error)
    {:db db}))

;
; load hotkeys
;

(re-frame/reg-event-fx
  ::server-load-hotkeys
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]
    (let [hotkeys (:move-target-hotkeys db)]
      (println "::server-load-hotkeys:" hotkeys)
      {
       :http-xhrio
       {:method          :get
        :uri             (gstring/format "/move-hotkeys")
        :timeout         30000
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::server-load-hotkeys-success]
        :on-failure      [::server-load-hotkeys-fail]}})))

(re-frame/reg-event-fx
  ::server-load-hotkeys-success
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ ret]]                                  ; currstate, and new state
    (io/display-notification "Load hotkeys: SUCCESS" :success)
    (println ret)
    (println "::server-load-hotkeys-success: type ret: " (type ret))
    ; because the server sends JSON, we want the map keywordized
    ; but...
    ; we need to convert the data from the wire before putting it
    ; into app-db
    {:db (assoc db :move-target-hotkeys
                   (io/transform-hotkey-server-to-client ret))}))

(re-frame/reg-event-fx
  ::server-load-hotkeys-fail
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]
    ; currstate, and new state
    (io/display-notification "Load hotkeys: FAIL" :error)
    {:db db}))

;
; redo last move
;

(re-frame/reg-event-fx
  ::handle-keyboard-repeat-last-command
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [lastmove (:move-save-last-operation db)]
      (io/display-notification "Repeating move card target..." :success)
      (println "::handle-keyboard-repeat-last-command: target: " lastmove)
      {
       :dispatch [::move-card-to-list [(:list-id lastmove)
                                       (:board-id lastmove)
                                       "top"]]})))


;
; hide/unhide keyboard shortcuts
;

(re-frame/reg-event-fx
  ::handle-keyboard-hide-unhide-shortcuts
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [currstate (:show-shortcuts db)]
      (io/display-notification
        (gstring/format "Toggling showing hotkeys: currently: %s" (str currstate))
        :success)
      {
       :db (assoc db :show-shortcuts
                     (not currstate))})))

;
; goto card
;

(re-frame/reg-event-fx
  ::scroll-to-top
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ id]]                                 ; currstate, and new state
    (println "::scroll-to-top: " id)
    ; document.body.scrollTop = document.documentElement.scrollTop = 0;
    ; https://stackoverflow.com/questions/4210798/how-to-scroll-to-top-of-page-with-javascript-jquery
    (set! (.-scrollTop (.-body js/document)) 0)
    (set! (.-scrollTop (.-documentElement js/document)) 0)
    {:db db}))

(re-frame/reg-event-fx
  ::route-goto-card-id
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ id]]                                 ; currstate, and new state
    (println "::goto-card: " id)
    (re-frame/dispatch [::scroll-to-top])
    (re-frame/dispatch [::reset-card])
    {:db (assoc db :materialized-card-position (dbh/get-pos-of-card-id id db))}))

;
; clipboard
;

(re-frame/reg-event-fx
  ::copy-to-clipboard
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ _]]                                  ; currstate, and new state
    (let [currcard (dbh/current-materialized-card db)
          text     (str (:name currcard)
                        "\n\n=== description: \n"
                        (:desc currcard))]
          ;_        (println currcard)]
      (clipboard/copy-text text)
      (println "::copy-to-clipboard: " text)
      (io/display-notification (str "Copied to clipboard: " (str (subs text 0 20) "..."))
                               :success))
    {:db db}))

;
; filter card selected
;
(re-frame/reg-event-fx
  ::filter-card-selected
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ id]]                                 ; currstate, and new state
    (println "::filter-card-selected: ")
    {:db db}))

;
; download csv
;
(re-frame/reg-event-fx
  ::download-list-as-csv
  [check-spec-interceptor]
  (fn [{:keys [db]} [_ id]]                                 ; currstate, and new state
    (println "::download-list-as-csv: ")
    (let [escaped-text (io/send-download-list-as-csv! (:current-list-cards-raw-from-trello db))
          _            (println escaped-text)]
      (clipboard/copy-text escaped-text)
      (io/display-notification (str "Copied cards to clipboard! " escaped-text)
                               :success))
    {:db db}))


;
; iphone
;
(re-frame/reg-event-db
  ::set-iphone-card-page-number
  [check-spec-interceptor]
  (fn [db [_ page]]                                            ; currstate, and newtext
    (println "::set-iphone-card-page-number: " page)
    (let [iphone-state (:iphone-state-and-view db)
          new-iphone-state (assoc iphone-state :page-num (js/parseInt page))]
      (assoc db :iphone-state-and-view new-iphone-state))))