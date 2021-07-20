;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-uber
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all :as test]
    [clojure.java.io :as jio]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.test-util :refer :all]
    [clojure.tools.build.util.zip :as zip]
    [clojure.tools.build.tasks.test-jar :as test-jar]))

(deftest test-uber
  (let [uber-path "target/p1-uber.jar"]
    (with-test-dir "test-data/p1"
      (api/set-project-root! (.getAbsolutePath *test-dir*))
      (api/javac {:class-dir "target/classes"
                  :src-dirs ["java"]})
      (api/copy-dir {:target-dir "target/classes"
                 :src-dirs ["src"]})
      (api/uber {:class-dir "target/classes"
                 :uber-file uber-path
                 :main 'foo.bar})
      (let [uf (jio/file (project-path uber-path))]
        (is (true? (.exists uf)))
        (is (= #{"META-INF/MANIFEST.MF" "foo/" "foo/bar.clj" "foo/Demo2.class" "foo/Demo1.class"}
               (set (map :name (zip/list-zip (project-path uber-path))))))
        (is (= (str/includes? (test-jar/slurp-manifest uf) "Main-Class: foo.bar")))))))

(deftest test-custom-manifest
  (let [uber-path "target/p1-uber.jar"]
    (with-test-dir "test-data/p1"
      (api/set-project-root! (.getAbsolutePath *test-dir*))
      (api/javac {:class-dir "target/classes"
                  :src-dirs ["java"]})
      (api/copy-dir {:target-dir "target/classes"
                     :src-dirs ["src"]})
      (api/uber {:class-dir "target/classes"
                 :uber-file uber-path
                 :main 'foo.bar
                 :manifest {"Main-Class" "baz" ;; overrides :main
                            'Custom-Thing 100}}) ;; stringify kvs
      (let [uf (jio/file (project-path uber-path))]
        (is (true? (.exists uf)))
        (is (= #{"META-INF/MANIFEST.MF" "foo/" "foo/bar.clj" "foo/Demo2.class" "foo/Demo1.class"}
              (set (map :name (zip/list-zip (project-path uber-path))))))
        (let [manifest-out (test-jar/slurp-manifest uf)]
          (is (= (str/includes? manifest-out "Main-Class: baz")))
          (is (= (str/includes? manifest-out "Custom-Thing: 100"))))))))

(comment
  (run-tests)
  )
