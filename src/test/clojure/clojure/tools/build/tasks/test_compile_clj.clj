;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-compile-clj
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.test :refer :all :as test]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.test-util :refer :all]))

(deftest test-compile
  (with-test-dir "test-data/p1"
    (api/set-project-root! (.getAbsolutePath *test-dir*))
    (api/compile-clj {:class-dir "target/classes"
                      :src-dirs ["src"]
                      :basis (api/create-basis nil)})
    (is (true? (.exists (jio/file (project-path "target/classes/foo/bar.class")))))
    (is (true? (.exists (jio/file (project-path "target/classes/foo/bar__init.class")))))
    (is (true? (.exists (jio/file (project-path "target/classes/foo/bar$hello.class")))))))

(defn find-java []
  (-> (api/process {:command-args [(if windows?
                                     "where"
                                     "which") "java"] :out :capture})
      :out
      str/split-lines
      first))

(deftest test-compile-passthrough-opts
  (let [java-cmd (find-java)]
    (with-test-dir "test-data/p1"
      (api/set-project-root! (.getAbsolutePath *test-dir*))
      (api/compile-clj {:class-dir "target/classes"
                        :src-dirs ["src"]
                        :basis (api/create-basis nil)
                        ;; pass these through to java command
                        :java-opts ["-Dhi=there"]
                        :use-cp-file :always
                        :java-cmd java-cmd})
      (is (true? (.exists (jio/file (project-path "target/classes/foo/bar.class")))))
      (is (true? (.exists (jio/file (project-path "target/classes/foo/bar__init.class")))))
      (is (true? (.exists (jio/file (project-path "target/classes/foo/bar$hello.class"))))))))

(comment
  (run-tests)
  )
