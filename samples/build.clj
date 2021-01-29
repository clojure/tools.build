(ns build
  (:require [clojure.tools.build.api :as b]))

;; Default build properties
(def params
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
          :uber-file "target/lib1-1.2.3-standalone.jar"
          :basis (b/load-basis)})

;; ==== deps.edn
;;  :aliases {:clj-paths ["src/main/clojure"]
;;            :resource-paths ["src/main/resources"]
;;            :build {:replace-deps {org.clojure/tools.build {:mvn/version "x.y.z"}
;;                                   org.slf4j/slf4j-nop {:mvn/version "1.7.25"}}
;;                    :replace-paths []
;;                    :ns-default build
;;                    :extra-paths ["samples"]}}

;; clojure -X:build clean
(defn clean
  [_]
  (b/clean #:build{:output-dir "." :target-dir "target"})) ;; TODO: cleanup to single dir

;; clojure -X:build jar
(defn jar
  [_]
  (b/sync-pom params)
  (b/copy params)
  (b/jar params))

;; clojure -X:build uber
(defn uber
  [_]
  (jar nil)
  (b/uber params))


(comment
  (clean nil)
  (jar nil)
  (uber nil)
  )