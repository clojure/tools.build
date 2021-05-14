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
    [clojure.tools.build.api :as api]
    [clojure.tools.build.test-util :refer :all]))

(deftest test-clean
  (with-test-dir "test-data/p1"
    ;; copy src into target, then clean, and check target dir is gone
    (doto #:build{:project-dir (.getAbsolutePath *test-dir*)
                  :dir "target"
                  :compile-dir "target/classes"
                  :copy-specs [{:from "src" :include "**"}]}
      api/copy
      api/clean)
    (is (false? (.exists (jio/file (project-path "target/classes")))))))

(comment
  (run-tests)
  )
