;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

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