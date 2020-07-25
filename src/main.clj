(ns main
  (:require
    [clojure.edn :as edn]
    [clojure.walk :as w]
    [clojure.core.match :as m]))

(def infile "/Users/genekim/src.local/trello-workflow/src/cljs/trello_workflow/events.cljs")

(defn read-forms
  "input: file"
  [s]
  ; https://stackoverflow.com/questions/6840425/with-clojure-read-read-string-function-how-do-i-read-in-a-clj-file-to-a-list-o
  ; this is needed to read in more than one form
  (let [code (read-string (str \( s \)))]
    (println "# form: " (count code))
    code))

(defn fn-name
  " print out name of first element in s-expr "
  [f]
  (println f)
  (doseq [l f]
    (println " -> " l)))

(defn re-frame-form?
  [sexpr]
  (let [sym (first sexpr)]
    ; can't use case here: symbols aren't known at compile-time
    (condp = sym
      're-frame/reg-event-db true
      're-frame/reg-event-fx true
      false)))

(defn re-frame-form-name
  [sexpr]
  (second sexpr))

(defn db-or-fx?
  " eval first form: so that we can extract ending map from -fx "
  [sexpr]
  (println sexpr)
  (condp = (first sexpr)
    're-frame/reg-event-db :db
    're-frame/reg-event-fx :fx
    nil))

(defn extract-fx [sexpr]
  " get :dispatch or :dispach-n from last form "
  (println sexpr)
  (let [[a b c fnlist] sexpr
        retmap (last fnlist)
        _ (println retmap)]
    (if (:dispatch retmap)
      [(:dispatch retmap)]
      (:dispatch-n retmap))))


; 0: (re-frame/reg-event-fx
; 1:  ::load-initial-state-success
; 2:  [check-spec-interceptor]
; 3:  (fn [{:keys [db]} [_ ret]]                                ; currstate, and new state
;    ; returns new card values: replace it in our (::current-list-cards db)
;    (println "::load-initial-state-success: " ret)
;    (let [username (:username ret)
;          fullname (:fullname ret)
;          _        (println "::load-initial-state-success: " username " " fullname)]
;      (println "::load-initial-state-success: " username, fullname)
;      (io/display-notification "got initial state..." :success)
;      {:dispatch [::server-load-hotkeys]
;       :db (assoc db :session {:username username :fullname fullname})})))

(defn find-dispatches
  ;" accumulate all events "
  ([sexpr events]
   (println sexpr)
   (println "walk: " sexpr)
   (println "===" (type sexpr))
   ; if (op arg1 args) (i.e., a list), check to see if it's re-frame
   ;   otherwise, recurse
   (if (or (seq? sexpr)
           (list? sexpr)
           (vector? sexpr))
     (if (= 're-frame/dispatch (first sexpr))
       (do
         (println "==== reframe! " (second sexpr))
         (second sexpr))
       ; else, recurse thru remaining args
       (let [retval (for [s (drop 1 sexpr)]
                      (find-dispatches s events))]
         ;(println "    returned: " retval)
         (mapcat identity (concat retval events))))))
   ;(if (and (list? sexpr)
   ;         (= 're-frame/dispatch (first sexpr)))
   ;  (do
   ;    (println "==== reframe!")
   ;    (second sexpr))
   ;  (for [s sexpr]
   ;    (find-dispatches s events))))
  ;" wrap it in a 'do expression, and call with starting state"
  ([sexpr]
   (println "name: " (second sexpr))
   (let [doexpr (concat ['do] (->> sexpr
                                   ; drop 3 lines: everything but the (fn) sexpr
                                   (drop 3)
                                   ; drop 2 forms: fn [_ _]
                                   first
                                   (drop 2)))]
     (find-dispatches doexpr []))))




(comment

  (def code (read-forms (slurp infile)))
  (count code)
  ; 110 forms

  (def r1 (nth code 3))
  (def r2 (nth code 11))

  (find-dispatches r2)

  (re-frame-form? (nth code 5))

  ; load all the forms
  (->> code
       (filter re-frame-form?))

  (->> code
       (filter re-frame-form?)
       (map re-frame-form-name))

  (+ 1 1)


  (fn-name (nth code 3))
  (-> code
      (nth 3))



  (nth code 3))



