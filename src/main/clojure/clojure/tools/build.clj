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
    [clojure.tools.deps.alpha.util.dir :as dir]
    [clojure.tools.build.file :as file])
  (:import
    [java.io File]))

(defn resolve-param
  "Resolve task param, flow param, or alias. First tries to resolve key
  as param or flow, repeatedly while finding keywords. Next tries to resolve
  alias if still a keyword. Returns nil if not resolved."
  [basis params key]
  (loop [k key]
    (let [v (get params k)]
      (cond
        (keyword? v) (recur v)
        (nil? v) (get-in basis [:aliases k])
        :else v))))

(defn maybe-resolve-param
  "Resolve task param but if not found, return possible-key instead"
  [basis params possible-key]
  (or (resolve-param basis params possible-key) possible-key))

(defn- resolve-task
  [task-sym]
  (let [task-fn (if (qualified-symbol? task-sym)
                  (requiring-resolve task-sym)
                  (resolve (symbol "clojure.tools.build.tasks" (str task-sym))))]
    (if task-fn
      task-fn
      (throw (ex-info (str "Unknown task: " task-sym) {})))))

(defn- load-basis
  [^File project-deps]
  (let [{:keys [root-edn project-edn]} (deps/find-edn-maps)
        project (if project-deps
                  (deps/slurp-deps project-deps)
                  project-edn)
        edns [root-edn project]
        master-edn (deps/merge-edns edns)]
    (deps/calc-basis master-edn)))

(defn build
  "Execute build:
     Load basis using project-deps (default=./deps.edn)
     Load build params - either a map or an alias
     Run tasks - task may have an arg map or alias, which is merged into the build params"
  [{:keys [project-dir params tasks]}]
  (let [project-dir-file (jio/file (or project-dir "."))
        project-deps-file (jio/file project-dir-file "deps.edn")
        basis (load-basis project-deps-file)
        params (if (keyword? params) (get-in basis [:aliases params]) params)
        default-params (assoc params :build/project-dir (.getAbsolutePath project-dir-file))]
    (require 'clojure.tools.build.tasks)
    (reduce
      (fn [flow [task-sym args]]
        (let [begin (System/currentTimeMillis)
              task-fn (resolve-task task-sym)
              args (if (keyword? args) (get-in basis [:aliases args]) args)
              arg-data (merge default-params args flow)
              res (task-fn basis arg-data)
              end (System/currentTimeMillis)]
          (println "Ran" task-sym "in" (- end begin) "ms")
          (reduce-kv (fn [f k v] (if (= "flow" (namespace k)) (assoc f k v) f)) flow res)))
      nil
      tasks)
    (println "Done!")))

(comment
  ;; Given aliases:
  ;; :clj-paths ["src/main/clojure"]
  ;  :java-paths ["java" "src/main/java"]
  ;  :resource-paths ["src/main/resources"]

  ;; clojure source lib
  (build
    '{:tasks [[clean] [sync-pom] [copy] [jar] [install]]
      :params {:build/target-dir "target1"
               :build/class-dir "classes"
               :build/copy-specs [{:from :clj-paths}]
               :build/src-pom "pom.xml"
               :build/lib my/lib1
               :build/version "1.2.3"}})

  ;; clojure source lib with git version template
  (build
    '{:tasks [[clean] [clojure.tools.build.extra/git-version] [sync-pom] [copy] [jar]]
      :params {:build/target-dir "target2"
               :build/class-dir "classes"
               :build/copy-specs [{:from :clj-paths}]
               :build/src-pom "pom.xml"
               :git-version/template "0.8.%s"
               :git-version/version> :flow/version
               :build/lib my/lib2
               :build/version :flow/version}})

  ;; java executable jar (no clojure!)
  (build
    '{:tasks [[clean] [javac] [sync-pom] [jar]]
      :params {:build/target-dir "target3"
               :build/class-dir "classes"
               :build/java-paths :java-paths
               :build/javac-opts ["-source" "8" "-target" "8"]
               :build/src-pom "pom.xml"
               :build/lib org.clojure/tools.build
               :build/version "0.1.0"
               :build/main-class clojure.tools.build.demo}})

  ;; compiled clojure lib jar w/metadata elided
  (build
    '{:tasks [[clean] [compile-clj] [copy] [jar]]
      :params {:build/target-dir "target4lib"
               :build/class-dir "classes"
               :build/clj-paths :clj-paths
               :build/filter-nses [clojure.tools.build]
               :build/compiler-opts {:elide-meta [:doc :file :line]}
               :build/copy-specs [{:from :resource-paths}]
               :build/src-pom "pom.xml"
               :build/lib org.clojure/tools.build
               :build/version "0.1.0"}})

  ;; compiled clojure app jar
  (build
    '{:tasks [[clean] [compile-clj] [copy] [jar]]
      :params {:build/target-dir "target4"
               :build/class-dir "classes"
               :build/clj-paths :clj-paths
               :build/copy-specs [{:from :resource-paths}]
               :build/src-pom "pom.xml"
               :build/lib org.clojure/tools.build
               :build/version "0.1.0"
               :build/main-class clojure.tools.build.demo}})

  ;; uber compiled jar
  (build
    '{:tasks [[clean] [sync-pom] [compile-clj] [copy] [uber]]
      :params {:build/target-dir "target5"
               :build/class-dir "classes"
               :build/clj-paths :clj-paths
               :build/copy-specs [{:from :resource-paths}]
               :build/src-pom "pom.xml"
               :build/lib my/lib1
               :build/version "1.2.3"}})

  ;; compiled lib w/classifier
  (build
    '{:tasks [[clean] [clojure.tools.build.extra/git-version] [sync-pom] [compile-clj] [jar]]
      :params {:build/target-dir "target6"
               :build/class-dir "classes"
               :build/src-pom "pom.xml"
               :build/lib org.clojure/tools.build
               :build/classifier "aot"
               :git-version/template "0.8.%s"
               :git-version/version> :flow/version
               :build/version :flow/version
               :build/clj-paths :clj-paths
               :build/filter-nses [clojure.tools.build]}})

  ;; zip
  (build
    '{:tasks [[clean] [copy] [zip]]
      :params {:build/target-dir "target-zip"
               :build/zip-dir "zip"
               :build/copy-to :build/zip-dir
               :build/copy-specs [{:include "README.md"}
                                  {:from :java-paths :include "**/*.java"}]
               :build/zip-name "java-source.zip"}})

  ;; process / format
  (build
    '{:tasks [[clean]
              [process {:build/command ["git" "rev-list" "HEAD" "--count"]
                        :build/out> :flow/rev}]
              [format-str {:build/template "example-%s.zip"
                           :build/args [:flow/rev]
                           :build/out> :flow/zip-name}]
              [copy {:build/copy-to :build/zip-dir
                     :build/copy-specs [{:include "README.md"
                                         :replace {"VERSION" :flow/rev
                                                   "FOO" "hi there"}}]}]
              [zip {:build/zip-name :flow/zip-name}]]
      :params {:build/target-dir "target-process"
               :build/zip-dir "zip"}})

  ;; merge data readers
  (let [basis (deps/calc-basis '{:deps {spyscope/spyscope {:mvn/version "0.1.6"}
                                        com.ladderlife/cellophane {:mvn/version "0.3.5"}}
                                 :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                                             "clojars" {:url "https://repo.clojars.org/"}}})]
    ((requiring-resolve 'clojure.tools.build.tasks/clean) basis '{:build/target-dir "target-merge"})
    ((requiring-resolve 'clojure.tools.build.tasks/uber) basis
      '{:build/target-dir "target-merge"
        :build/class-dir "classes"
        :build/lib test/merge
        :build/version "1.2.3"}))
  )
