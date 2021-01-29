(ns build
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.build :as tbuild]
    [clojure.tools.build.tasks.dirs :refer [dirs]]
    [clojure.tools.build.tasks.clean :refer [clean]]
    [clojure.tools.build.tasks.sync-pom :refer [sync-pom]]
    [clojure.tools.build.tasks.copy :refer [copy]]
    [clojure.tools.build.tasks.jar :as jar]))

(defn jar
  [opts]
  (let [basis (tbuild/load-basis (jio/file "deps.edn"))
        params (merge {:build/lib 'my/lib1
                       :build/version "1.2.3"
                       :build/clj-paths :clj-paths
                       :build/copy-specs [{:from :clj-paths}]
                       :build/project-dir "."
                       :build/output-dir "."}
                 opts)
        params (merge params (dirs basis params))]
    (clean basis params)
    (sync-pom basis params)
    (copy basis params)
    (jar/jar basis params)))

(comment
  (jar nil)
  )