(ns build
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.build :as tbuild]
    [clojure.tools.build.tasks.dirs :refer [dirs]]
    [clojure.tools.build.tasks.clean :refer [clean]]
    [clojure.tools.build.tasks.sync-pom :refer [sync-pom]]
    [clojure.tools.build.tasks.copy :refer [copy]]
    [clojure.tools.build.tasks.jar :as jar]
    [clojure.tools.build.tasks.uber :as uber]))

(def defaults
  #:build{:lib 'my/lib1
          :version "1.2.3"
          :clj-paths :clj-paths
          :copy-specs [{:from :clj-paths}]
          :project-dir "."
          :output-dir "."})

(defn jar
  [opts]
  (let [basis (tbuild/load-basis (jio/file "deps.edn"))
        params (merge defaults opts)
        params (merge params (dirs basis params))]
    (clean basis params)
    (sync-pom basis params)
    (copy basis params)
    (jar/jar basis params)))

(defn uber
  [opts]
  (let [basis (tbuild/load-basis (jio/file "deps.edn"))
        params (merge defaults opts)
        params (merge params (dirs basis params))]
    (clean basis params)
    (sync-pom basis params)
    (copy basis params)
    (jar/jar basis params)
    (uber/uber basis params)))


(comment
  (jar nil)
  (uber nil)
  )