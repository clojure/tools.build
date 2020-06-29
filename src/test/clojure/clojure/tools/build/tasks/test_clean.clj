(ns clojure.tools.build.tasks.test-clean
  (:require
    [clojure.test :refer :all :as test]
    [clojure.java.io :as jio]
    [clojure.tools.build.test-util :refer :all]))

(deftest test-clean
  ;; copy src into target, then clean, and check target dir is gone
  (let [out-dir (build-project '{:project-dir "test-data/p1"
                                 :tasks [[copy] [clean]]
                                 :params {:build/target-dir "target"
                                          :build/class-dir "target/classes"
                                          :build/copy-specs [{:from "src"}]}})]
    (is (false? (.exists (jio/file out-dir "target"))))))

(comment
  (run-tests)
  )