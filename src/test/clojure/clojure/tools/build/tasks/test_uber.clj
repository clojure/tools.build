;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-uber
  (:require
    [clojure.test :refer :all :as test]
    [clojure.java.io :as jio]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.test-util :refer :all]
    [clojure.tools.build.util.zip :as zip])
  (:import [java.util.zip ZipFile ZipOutputStream ZipEntry]))

(defn slurp-manifest
  [z]
  (let [zip-file (jio/file z)]
    (with-open [zip (ZipFile. zip-file)]
      (let [^ZipEntry ze (.getEntry zip "META-INF/MANIFEST.MF")]
        (when ze
          (slurp (.getInputStream zip ze)))))))

(deftest test-uber
  (let [uber-path "target/p1-uber.jar"]
    (with-test-dir "test-data/p1"
      (api/set-project-root! (.getAbsolutePath *test-dir*))
      (api/javac {:class-dir "target/classes"
                  :java-dirs ["java"]})
      (api/copy {:target-dir "target/classes"
                 :src-specs [{:src-dir "src" :include "**"}]})
      (api/uber {:class-dir "target/classes"
                 :uber-file uber-path
                 :main 'foo.bar})
      (let [uf (jio/file (project-path uber-path))]
        (is (true? (.exists uf)))
        (is (= #{"META-INF/MANIFEST.MF" "foo/" "foo/bar.clj" "foo/Demo2.class" "foo/Demo1.class"}
               (set (map :name (zip/list-zip (project-path uber-path))))))
        (is (= (clojure.string/includes? (slurp-manifest uf) "Main-Class: foo.bar")))))))

(comment
  (run-tests)
  )
