(ns duratom.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [duratom.core :as d]
            [duratom.backends :as b]
            [glimmer.ratom :as ratom]
            [jolt.fs :as fs]))

(defn- uniq [label]
  (str "/tmp/" label "-" (System/currentTimeMillis) "-" (rand-int 1000000)))

;; A custom backend written against the protocol, to prove the extension seam.
(defrecord MemBackend [store]
  b/StorageBackend
  (load-state [_] @store)
  (persist! [_ v] (reset! store v))
  (clear! [_] (reset! store nil) nil)
  (close-backend [_] nil))

(deftest backend-protocol-is-the-extension-seam
  (let [store (clojure.core/atom nil)
        a (d/make-duratom (->MemBackend store) {:m 0})]
    (is (= {:m 0} @a))
    (swap! a assoc :m 1)
    (is (= {:m 1} @store))
    (is (= {:m 1} (d/backend-snapshot a)))))

(deftest file-backend-persists-and-round-trips
  (let [path (str (uniq "duratom-file") ".edn")
        a (d/file-atom path {:x 1 :y 2})]
    (try
      (testing "initial value is persisted"
        (is (= {:x 1 :y 2} @a))
        (is (= {:x 1 :y 2} (d/backend-snapshot a))))
      (testing "reset! persists"
        (reset! a {:z 3})
        (is (= {:z 3} @a))
        (is (= {:z 3} (d/backend-snapshot a))))
      (testing "swap! persists"
        (swap! a assoc :w 4)
        (is (= {:z 3 :w 4} @a))
        (is (= {:z 3 :w 4} (d/backend-snapshot a))))
      (testing "a new duratom over the same path reads persisted state"
        (let [a2 (d/file-atom path)]
          (is (= {:z 3 :w 4} @a2))
          (d/destroy a2)))
      (finally
        (d/destroy a)
        (when (fs/exists? path) (fs/delete path))))))

(deftest sql-backend-persists-and-round-trips
  (let [db-path (str (uniq "duratom-sql") ".db")
        spec (str "sqlite:" db-path)
        a (d/sql-atom spec "duratom_state" {:n 1})]
    (try
      (testing "initial value is persisted"
        (is (= {:n 1} @a))
        (is (= {:n 1} (d/backend-snapshot a))))
      (testing "reset! persists"
        (reset! a {:n 2})
        (is (= {:n 2} @a))
        (is (= {:n 2} (d/backend-snapshot a))))
      (testing "swap! persists"
        (swap! a update :n inc)
        (is (= {:n 3} @a))
        (is (= {:n 3} (d/backend-snapshot a))))
      (testing "a new duratom over the same table reads persisted state"
        (let [a2 (d/sql-atom spec "duratom_state")]
          (is (= {:n 3} @a2))
          (d/destroy a2)))
      (finally
        (d/destroy a)
        (when (fs/exists? db-path) (fs/delete db-path))))))

(deftest duratom-is-a-glimmer-reactive-cell
  (let [path (str (uniq "duratom-react") ".edn")
        a (d/file-atom path 2)
        sq (ratom/reaction (* @a @a))]
    (try
      (is (= 4 @sq))
      (reset! a 5)
      (is (= 25 @sq))
      (finally
        (d/destroy a)
        (when (fs/exists? path) (fs/delete path))))))
