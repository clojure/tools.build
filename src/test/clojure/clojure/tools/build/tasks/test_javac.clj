;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-javac
  (:require
    [clojure.test :refer :all :as test]
    [clojure.java.io :as jio]
    [clojure.tools.build.test-util :refer :all]
    [clojure.tools.build.task.process :as process]))

(deftest test-javac
  (let [out-dir (build-project '{:project-dir "test-data/p1"
                                 :tasks [[javac]]
                                 :params {:build/compile-dir "target/classes"
                                          :build/java-paths ["java"]}})]
    (is (true? (.exists (jio/file out-dir "target/classes/foo/Demo1.class"))))
    (is (true? (.exists (jio/file out-dir "target/classes/foo/Demo2.class"))))
    (let [class-path (.getPath (jio/file out-dir "target/classes"))]
      (is (= "Hello" (process/invoke ["java" "-cp" class-path "foo.Demo1"])))
      (is (= "Hello" (process/invoke ["java" "-cp" class-path "foo.Demo2"]))))))

(comment
  (run-tests)
  )
