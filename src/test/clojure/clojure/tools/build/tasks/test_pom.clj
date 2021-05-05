;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-pom
  (:require
    [clojure.test :refer :all :as test]
    [clojure.java.io :as jio]
    [clojure.repl :as repl]
    [clojure.string :as str]
    [clojure.zip :as zip]
    [clojure.data.xml :as xml]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.build.task.file :as file]
    [clojure.tools.build.tasks.sync-pom :as sync-pom]
    [clojure.tools.build.test-util :refer :all])
  (:import
    [java.io File InputStream]
    [java.util Properties]))

(set! *warn-on-reflection* true)

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defn read-xml
  [^File f]
  (when (.exists f)
    (#'sync-pom/parse-xml (jio/reader f))))

(defn xml-path-val
  [root tag-path]
  (let [z (zip/zipper xml/element? :content nil root)]
    (loop [[tag & more-tags :as tags] tag-path, parent z, child (zip/down z)]
      (if child
        (let [node (zip/node child)]
          (if (= tag (:tag node))
            (if (seq more-tags)
              (recur more-tags child (zip/down child))
              (:content node))
            (recur tags parent (zip/right child))))
        nil))))

(defn read-props
  [^File f]
  (let [props (Properties.)]
    (when (.exists f)
      (doto props
        (.load ^InputStream (jio/input-stream f))))))

(deftest test-new-pom
  (let [out-dir (build-project '{:project-dir "test-data/p1"
                                 :tasks [[clean {:build/dir "target"}] [sync-pom]]
                                 :params {:build/lib test/p1
                                          :build/version "1.2.3"
                                          :build/clj-paths :clj-paths
                                          :build/resource-paths :resource-paths}})
        pom-dir (jio/file out-dir "target" "classes" "META-INF" "maven" "test" "p1")
        pom-out (jio/file pom-dir "pom.xml")
        pom (read-xml pom-out)
        prop-out (jio/file pom-dir "pom.properties")
        props (read-props prop-out)]
    ;; check xml out
    (is (.exists pom-out))
    (are [path val] (= val (xml-path-val pom path))
      [::pom/packaging] ["jar"]
      [::pom/groupId] ["test"]
      [::pom/artifactId] ["p1"]
      [::pom/version] ["1.2.3"]
      [::pom/name] ["p1"]
      [::pom/build ::pom/sourceDirectory] ["src"]
      [::pom/build ::pom/resources ::pom/resource ::pom/directory] ["resources"])
    (= 2 (count (xml-path-val pom [::pom/dependencies])))
    (= 1 (count (xml-path-val pom [::pom/repositories])))
    ;; check properties out
    (is (.exists prop-out))
    (is (submap? {"groupId" "test", "artifactId" "p1", "version" "1.2.3"} props))))

(deftest test-update-existing-pom
  (let [out-dir (build-project '{:project-dir "test-data/p2"
                                 :tasks [[clean {:build/dir "target"}] [sync-pom]]
                                 :params {:build/lib test/p2
                                          :build/version "1.2.3"
                                          :build/clj-paths :clj-paths
                                          :build/resource-paths :resource-paths}})
        pom-dir (jio/file out-dir "target" "classes" "META-INF" "maven" "test" "p2")
        pom-out (jio/file pom-dir "pom.xml")
        pom (read-xml pom-out)
        prop-out (jio/file pom-dir "pom.properties")
        props (read-props prop-out)]
    ;; check xml out
    (is (.exists pom-out))
    (are [path val] (= val (xml-path-val pom path))
      [::pom/packaging] ["jar"]
      [::pom/groupId] ["test"]
      [::pom/artifactId] ["p2"]
      [::pom/version] ["1.2.3"]
      [::pom/name] ["p2"]
      [::pom/build ::pom/sourceDirectory] ["src"]
      [::pom/build ::pom/resources ::pom/resource ::pom/directory] ["resources"]
      )
    (= 2 (count (xml-path-val pom [::pom/dependencies])))
    (= 1 (count (xml-path-val pom [::pom/repositories])))
    ;; check properties out
    (is (.exists prop-out))
    (is (submap? {"groupId" "test", "artifactId" "p2", "version" "1.2.3"} props))))

;; check that optional deps are marked optional
(deftest test-optional
  (let [out-dir (build-project '{:project-dir "test-data/p3"
                                 :tasks [[clean {:build/dir "target"}] [sync-pom]]
                                 :params {:build/lib test/p3
                                          :build/version "1.2.3"}})
        pom-dir (jio/file out-dir "target" "classes" "META-INF" "maven" "test" "p3")
        pom-out (jio/file pom-dir "pom.xml")]
    (is (.exists pom-out))
    (let [generated (slurp pom-out)]
      (is (str/includes? generated "core.async"))
      (is (str/includes? generated "<optional>true</optional>")))))

(comment
  (run-tests)
  )
