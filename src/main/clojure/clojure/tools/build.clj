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

(defn build
  "Execute build:
     Load basis using project-deps (default=./deps.edn)
     Load build params - either a map or an alias
     Run tasks - task may have an arg map or alias, which is merged into the build params"
  [{:keys [project-deps params tasks]}]
  (let [{:keys [install-edn user-edn project-edn]} (reader/find-edn-maps)
        ordered-edns (remove nil? [install-edn user-edn project-edn])
        master-edn (deps/merge-edns ordered-edns)
        basis (deps/calc-basis master-edn)
        default-params (resolve-alias basis params)]
    ;(println "params" params)
    ;(println "default-params" default-params)
    (reduce
      (fn [flow [task-sym args]]
        (let [resolved-task (if (qualified-symbol? task-sym)
                              task-sym
                              (symbol "clojure.tools.build.tasks" (str task-sym)))
              _ (println "\nRunning task" (name resolved-task))
              task-fn (requiring-resolve resolved-task)
              arg-data (merge default-params (resolve-alias basis args) flow)
              _ (clojure.pprint/pprint arg-data)
              res (task-fn basis arg-data)]
          (if-let [err (:error res)]
            (throw (ex-info err {:task resolved-task, :arg-data arg-data}))
            (merge flow res))))
      nil
      tasks)))

(comment
  (require
    '[clojure.tools.build.tasks :refer :all]
    '[clojure.tools.build.extra :refer :all])

  ;; Given aliases:
  ;; :clj-paths ["src/main/clojure"]
  ;  :java-paths ["java" "src/main/java"]
  ;  :resource-paths ["resources"]

  ;; clojure source lib
  (build
    '{:tasks [[clean] [sync-pom] [include-resources] [jar]]
      :params {;; these could be defaulted as almost everyone needs these
               :build/target-dir "target1"
               :build/class-dir "target1/classes"
               :build/resources :clj-paths
               :build/src-pom "pom.xml"

               ;; app specific
               :build/lib my/lib1
               :build/version "1.2.3"}})

  ;; clojure source lib with git version template
  (build
    '{:tasks [[clean] [clojure.tools.build.extra/git-version] [sync-pom] [include-resources] [jar]]
      :params {:build/target-dir "target2"
               :build/class-dir "target2/classes"
               :build/resources :rpaths
               :build/src-pom "pom.xml"

               :git-version/template "0.8.VERSION"
               :git-version/version> :flow/version
               :build/lib my/lib2
               :build/version :flow/version}})

  ;; java executable jar (no clojure!)
  (build
    '{:tasks [[clean] [javac] [sync-pom] [include-resources] [jar]]
      :params {:build/target-dir "target3"
               :build/class-dir "target3/classes"
               :build/java-paths :java-paths ; ["java" "src/main/java"]
               :build/resources :resource-paths ; ["resources"]
               :build/src-pom "pom.xml"
               :build/lib org.clojure/tools.build
               :build/version "0.1.0"
               :build/main-class clojure.tools.build.demo}})

  ;; compiled clojure app jar
  (build
    '{:tasks [[clean] [compile-clj] [include-resources] [jar]]
      :params {:build/target-dir "target4"
               :build/class-dir "target4/classes"
               :build/clj-paths :clj-paths ; ["src"]
               :build/resources :resource-paths ; ["resources"]
               :build/src-pom "pom.xml"
               :build/lib org.clojure/tools.build
               :build/version "0.1.0"
               :build/main-class clojure.tools.build.demo}})

  ;; uber jar
  (build
    '{:tasks [[clean] [sync-pom] [include-resources] [jar] [uber]]
      :params {:build/target-dir "target5"
               :build/class-dir "target5/classes"
               :build/resources :paths
               :build/src-pom "pom.xml"
               :build/lib my/lib1
               :build/version "1.2.3"}})

  ;; src lib AND compiled lib
  (build
    '{:tasks [[clean] [sync-pom] [jar]
              [compile-clj] [jar {:build/classifier "aot"}]]
      :params {:build/target-dir "target6"
               :build/src-pom "pom.xml"
               :build/lib org.clojure/tools.build
               :git-version/version-template "0.8.VERSION"
               :git-version/version :flow/version
               :build/version :flow/version
               :build/resource-dirs :resource-paths
               :build/clj-paths :clj-paths}})

  )
