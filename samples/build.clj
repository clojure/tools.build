(ns build
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.build :as tbuild]
    [clojure.tools.build.tasks.clean :as clean]
    [clojure.tools.build.tasks.sync-pom :refer [sync-pom]]
    [clojure.tools.build.tasks.copy :refer [copy]]
    [clojure.tools.build.tasks.jar :as jar]
    [clojure.tools.build.tasks.uber :as uber]))

;; Default build properties
(def defaults
  #:build{:lib 'my/lib1
          :version "1.2.3"
          :clj-paths :clj-paths
          :copy-specs [{:from :clj-paths}]
          :project-dir "."
          :output-dir "."
          :target-dir "target"
          :class-dir "target/classes"
          :pom-dir "target/classes/META-INF/maven/my/lib1" ;; TODO: cleanup in sync-pom
          :jar-file "target/lib1-1.2.3.jar"
          :uber-file "target/lib1-1.2.3-standalone.jar"})

;; Deps basis
(def basis (tbuild/load-basis (jio/file "deps.edn")))

;; ==== deps.edn
;;  :aliases {:clj-paths ["src/main/clojure"]
;;            :resource-paths ["src/main/resources"]
;;  :build   {:extra-deps {org.slf4j/slf4j-nop {:mvn/version "1.7.25"}}
;;            :ns-default build
;;            :extra-paths ["samples"]}}

;; clojure -X:build clean
(defn clean
  [opts]
  (clean/clean basis (merge defaults opts)))

;; clojure -X:build jar
(defn jar
  [opts]
  (let [params (merge defaults opts)]
    (sync-pom basis params)
    (copy basis params)
    (jar/jar basis params)))

;; clojure -X:build uber
(defn uber
  [opts]
  (jar opts)
  (uber/uber basis (merge defaults opts)))

(comment
  (clean nil)
  (jar nil)
  (uber nil)
  )