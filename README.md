# duratom

A durable atom for [jolt](https://github.com/jolt-lang/jolt): an `@`-derefable
reactive cell that persists every mutation to a pluggable backend. It implements
glimmer's `IReactiveCell` protocol, so a duratom drops into any glimmer program
unchanged — `@`, `reset!`, `swap!`, `cursor` and `reaction` all work while each
change is written through to storage.

## Install

```clojure
;; deps.edn
{:deps {io.github.jolt-lang/duratom
        {:git/url "https://github.com/jolt-lang/duratom.git"
         :git/sha "<full-sha>"}}}
```

## Quick start

```clojure
(require '[duratom.core :as d])

(def counts (d/file-atom "counts.edn" {:clicks 0}))

@counts                     ;; => {:clicks 0}
(swap! counts update :clicks inc)
@counts                     ;; => {:clicks 1}

(d/backend-snapshot counts) ;; => {:clicks 1} — read back from disk

(d/destroy counts)          ;; delete the file and release the backend
```

Every `reset!` / `swap!` persists, so a new atom over the same path reads the
stored state:

```clojure
@(d/file-atom "counts.edn") ;; => {:clicks 1}
```

`init` may be a value, a no-arg fn, or a delay; it is ignored when the backend
already holds a value.

## SQL backend

`sql-atom` persists a single EDN value to an `(id, value)` table through
[jolt-lang/db](https://github.com/jolt-lang/db) — sqlite or postgres:

```clojure
(def counters (d/sql-atom "sqlite:state.db" "counters" {:n 0}))
;; postgres:
(def counters (d/sql-atom "postgres://user:pass@127.0.0.1:5432/app" "counters" {:n 0}))
```

The table is created if missing. The connection stays open for the atom's
lifetime, so `"sqlite::memory:"` keeps its data across mutations. A `dbspec` map
(`{:vendor :sqlite :name ...}`) works too.

## The StorageBackend protocol

A backend is anything implementing `duratom.backends/StorageBackend`. That is the
seam for adding a new durable store — a key-value service, a remote API, …:

```clojure
(require '[duratom.core :as d]
         '[duratom.backends :as b])

(defrecord MyBackend [conn]
  b/StorageBackend
  (load-state [_]     ...)   ; read the persisted value, or nil
  (persist! [_ v]     ...)   ; store v, return v
  (clear! [_]         ...)   ; remove the stored value
  (close-backend [_]  ...))  ; release resources

(def a (d/make-duratom (->MyBackend conn) {:initial true}))
```

`load-state` returning nil means "empty": `make-duratom` starts the atom at its
`init` argument and persists it.

## Reactive integration with glimmer

`Duratom` implements `glimmer.ratom/IReactiveCell`, so it composes with the rest
of the reactive model:

```clojure
(require '[glimmer.ratom :as r])

(def a  (d/file-atom "n.edn" 2))
(def sq (r/reaction (* @a @a)))   ;; 4

(reset! a 5)
@sq                               ;; 25 — recomputed, and 5 persisted
```

## Development

```bash
jolt -M:test
```

The suite runs headless: file backend, sqlite backend via jolt-lang/db, the
protocol seam, and glimmer reaction composition.

## License

Eclipse Public License, same as Clojure. Forked from
[jimpil/duratom](https://github.com/jimpil/duratom).
