(ns duratom.core
  "A durable atom: a glimmer reactive cell whose mutations are persisted through
  a pluggable `duratom.backends/StorageBackend`.

  Because it implements `glimmer.ratom/IReactiveCell`, a duratom works everywhere
  a glimmer atom does — @, reset!, swap!, cursor, reaction — while persisting
  every change to its backend."
  (:require [glimmer.ratom :as ratom]
            [duratom.backends :as backends]
            [duratom.utils :as ut]))

(defrecord Duratom [backend state watches]
  ratom/IReactiveCell
  (-value [_] @state)
  (-reset! [this v]
    (let [changed?
          (locking this
            (let [old @state]
              (when (not= old v)
                (backends/persist! backend v)
                (reset! state v)
                true)))]
      (when changed?
        (ratom/-notify-watches! this))
      v))
  (-add-watch! [_ w] (swap! watches conj w))
  (-remove-watch! [_ w] (swap! watches disj w))
  (-notify-watches! [this] (doseq [w @watches] (w this))))

(defn make-duratom
  "Build a durable atom over `backend` (any `duratom.backends/StorageBackend`).
  If the backend already holds a value it becomes the initial state; otherwise
  the atom starts at `init` and persists it."
  [backend init]
  (let [d (->Duratom backend
                     (clojure.core/atom nil)
                     (clojure.core/atom #{}))]
    (if-let [stored (backends/load-state backend)]
      (reset! (:state d) stored)
      (reset! d (ut/->init init)))
    d))

(defn file-atom
  "A durable atom persisted as EDN to `path` on the local filesystem."
  ([path] (file-atom path nil))
  ([path init] (make-duratom (backends/file-backend path) init)))

(defn sql-atom
  "A durable atom persisted to `table` over a jolt-lang/db `dbspec`
  (e.g. \"sqlite::memory:\" or a postgres uri)."
  ([dbspec table] (sql-atom dbspec table 0 nil))
  ([dbspec table init] (sql-atom dbspec table 0 init))
  ([dbspec table row-id init]
   (make-duratom (backends/sql-backend dbspec table row-id) init)))

(defn backend-snapshot
  "The value currently persisted in `dura`'s backend, read fresh."
  [dura]
  (backends/load-state (:backend dura)))

(defn destroy
  "Remove `dura`'s persisted value and release its backend resources."
  [dura]
  (backends/clear! (:backend dura))
  (backends/close-backend (:backend dura))
  nil)
