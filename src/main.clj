(ns main
  (:require
    [clojure.edn :as edn]
    [clojure.walk :as w]
    [clojure.core.match :as m]
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn >def | ? =>]]))

(>defn test-guardrails
  [i] [integer? => nil?]
  1)

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
        ;retmap (last fnlist)
        lastfnform (last fnlist)
        ;_          (println "lastfn: " lastfnform)
        retmap     (if (map? lastfnform)
                     lastfnform
                     (if (and (sequential? lastfnform)
                              (= 'let (first lastfnform)))
                       (last lastfnform)))
        ;_          (println "retmap: " retmap)
        retv       (if (:dispatch retmap)
                     (:dispatch retmap)
                     (flatten (:dispatch-n retmap)))]
    (if (or (not (sequential? retv))
            (empty? retv))
      nil
      retv)))


(>defn find-dispatches
  ;" accumulate all events "
  ([sexpr events]
   [any? vector? => (s/nilable seq?)]
   ;(println sexpr)
   ;(println "walk: " sexpr)
   ;(println "===" (type sexpr))
   ; if (op arg1 args) (i.e., a list), check to see if it's re-frame
   ;   otherwise, recurse
   (if (or (seq? sexpr)
           (list? sexpr)
           (vector? sexpr))
     (if (= 're-frame/dispatch (first sexpr))
       (do
         ;(println "==== reframe! " (second sexpr))
         (second sexpr))
       ; else, recurse thru remaining args
       (let [retval (for [s (drop 1 sexpr)]
                      (find-dispatches s events))]
         ;(println "    returned: " retval)
         ; flatten
         (remove nil? (flatten (concat retval events)))))))
  ; wrap it in a 'do expression, and call with starting state
  ;
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
  ([sexpr]
   [sequential? => (s/nilable seq?)]
   (println "name: " (second sexpr))
   (let [doexpr   (concat ['do] (->> sexpr
                                     ; drop 3 lines: everything but the (fn) sexpr
                                     (drop 3)
                                     ; drop 2 forms: fn [_ _]
                                     first
                                     (drop 2)))
         ds1      (find-dispatches doexpr [])
         ds2      (extract-fx sexpr)
         combined (concat ds1 ds2)]
         ;_        (println "ds1: " ds1)
         ;_        (println "ds2: " ds2)
         ;_        (println "combined: " combined)]
     combined)))


(>defn gen-events
  [] [=> sequential?]
  (let [code  (read-forms (slurp infile))
        forms (filter re-frame-form? code)
        dag   (map vector
                   (map re-frame-form-name forms)
                   (map find-dispatches forms))]
    dag))


(comment

  (gen-events)

  (def code (read-forms (slurp infile)))
  (count code)
  ; 110 forms

  (def r1 (nth code 3))
  (def r2 (nth code 11))

  (find-dispatches r2)
  (nth code 33)
  (find-dispatches (nth code 33))

  (re-frame-form? (nth code 5))

  ; load all the forms
  (->> code
       (filter re-frame-form?))

  (def evnames (->> code
                    (filter re-frame-form?)
                    (map re-frame-form-name)))

  (->> code
       (filter re-frame-form?)
       (map find-dispatches))



  (fn-name (nth code 3))
  (-> code
      (nth 3))



  (nth code 3))



