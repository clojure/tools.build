(require '[babashka.pods :as pods])
;; Load tools-deps-native pod which defines clojure.tools.deps.alpha.
;; This assumes the binar tools-deps-native is on your PATH
;; You can change the call to load from an absolute or relative path instead.
(pods/load-pod "tools-deps-native")

(require '[spartan.spec]) ;; defines clojure.spec.alpha

(ns build
  (:require [clojure.tools.build.api :as b]))

(def version "0.1.0")
(def class-dir "target/classes")
(def jar-file (format "target/example-%s.jar" version))
(def lib 'my/example)

(defn basis [_]
  (b/create-basis {:project "deps.edn"}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn write-pom [{:keys [basis]}]
  (b/write-pom
   {:basis     basis
    :src-dirs  ["src"]
    :class-dir class-dir
    :lib lib
    :version version}))

(defn jar [{:keys [basis]}]
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar
   {:basis     basis
    :src-dirs  ["src"]
    :class-dir class-dir
    :main      "example.core"
    :jar-file  jar-file}))

(defn install [{:keys [basis]}]
  (b/install {:basis basis
              :class-dir class-dir
              :jar-file jar-file
              :lib lib
              :version version}))
