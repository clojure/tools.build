;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build
 (:require
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as reader]))

(defn resolve-alias
  [basis key]
  (if (keyword? key)
    (-> basis :aliases key)
    key))

(def default-params
  "Build param defaults"
  {:build/target-dir "target"
   :build/clj-paths :clj-paths ; ["src"]
   :build/java-paths :java-paths ; ["java" "src/main/java"]
   :build/resource-dirs :resource-paths ; ["resources"]
   :build/src-pom "pom.xml"})

(defn build-params
  [basis & param-srcs]
  "Load build-params from param-srcs and merge those into basis under
  :build/params. Each param-src can either be a deps alias or a map."
  (println "param-srcs" param-srcs)
  (let [params (map #(resolve-alias basis %) param-srcs)]
    (update basis :build/params #(apply merge % params))))

(comment
  (require '[clojure.tools.build.tasks :refer :all])

  ;; basic clojure lib build
  (->
    (reader/find-edn-maps)
    reader/order-edn-maps
    deps/merge-edns
    deps/calc-basis
    (build-params default-params :build-info)
    clean sync-pom jar end)

  ;; javac, executable jar
  (->
    (reader/find-edn-maps)
    reader/order-edn-maps
    deps/merge-edns
    deps/calc-basis
    (build-params default-params :build-info {:build/main-class 'foo.Demo1})
    clean javac jar end)

  ;; aot app jar
  (->
    (reader/find-edn-maps)
    reader/order-edn-maps
    deps/merge-edns
    deps/calc-basis
    (build-params default-params :build-info {:build/main-class 'clojure.tools.build.demo})
    clean aot jar end)

  ;; uber jar
  (->
    (reader/find-edn-maps)
    reader/order-edn-maps
    deps/merge-edns
    deps/calc-basis
    (build-params default-params :build-info)
    clean sync-pom jar uber end)
  )
