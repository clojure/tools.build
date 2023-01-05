;; This is a test build.clj, referenced in the README

(ns build
  (:require [clojure.tools.build.api :as b]))

(def version "0.1.0")
(def class-dir "target/classes")

(defn basis [_]
  (b/create-basis {:project "deps.edn"}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn write-pom [{:keys [basis]}]
  (b/write-pom
   {:basis     basis
    :src-dirs  ["src"]
    :class-dir class-dir
    :lib 'my/example
    :version version}))

(defn jar [{:keys [basis]}]
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar
   {:basis     basis
    :src-dirs  ["src"]
    :class-dir class-dir
    :main      "example.core"
    :jar-file  (format "target/example-%s.jar" version)}))
