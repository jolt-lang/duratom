(ns duratom.test-runner
  (:require [clojure.test :as t]
            [duratom.core-test]))

(defn -main [& _]
  (let [result (t/run-tests 'duratom.core-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (throw (ex-info "duratom tests failed" result)))
    (println "all passed")))
