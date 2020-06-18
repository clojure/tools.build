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
    [clojure.pprint :as pprint]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.dir :as dir])
  (:import
    [java.io File]))

(defn- resolve-task
  [task-sym]
  (let [task-fn (if (qualified-symbol? task-sym)
                  (requiring-resolve task-sym)
                  (let [tname (str task-sym)
                        tns (str "clojure.tools.build.tasks." tname)
                        tsym (symbol tns tname)]
                    (require (symbol tns))
                    (resolve tsym)))]
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

(defmacro log
  [verbose & msgs]
  `(when ~verbose
     (println ~@msgs)))

(defmacro log-map
  [verbose m]
  `(when ~verbose
     (binding [*print-namespace-maps* false]
       (pprint/pprint ~m))))

(defn build
  "Execute build:
     Load basis from project-dir (default = current directory)
     Load build params - either a map or an alias
     Run tasks - task may have an arg map or alias, which is merged into the build params
     Output is relative to output-dir (default = current directory)"
  [{:keys [project-dir output-dir params tasks verbose]}]
  (let [project-dir-file (jio/file (or project-dir "."))
        project-deps-file (jio/file project-dir-file "deps.edn")
        basis (load-basis project-deps-file)
        params (if (keyword? params) (get-in basis [:aliases params]) params)
        output-dir-file (if output-dir (jio/file output-dir) project-dir-file)
        default-params (assoc params
                         :build/project-dir (.getCanonicalPath project-dir-file)
                         :build/output-dir (.getCanonicalPath output-dir-file))]
    (log verbose "Build params:")
    (log-map verbose default-params)
    (reduce
      (fn [flow [task-sym args]]
        (log verbose)
        (log verbose "Running" task-sym)
        (let [begin (System/currentTimeMillis)
              task-fn (resolve-task task-sym)
              args (if (keyword? args) (get-in basis [:aliases args]) args)
              arg-data (merge flow args)
              _ (log-map verbose arg-data)
              res (task-fn basis arg-data)
              end (System/currentTimeMillis)]
          (println "Ran" task-sym "in" (- end begin) "ms")
          (merge flow res)))
      default-params
      tasks)
    (println "Done!")))

(comment
  ;; Given aliases:
  ;; :clj-paths ["src/main/clojure"]
  ;  :java-paths ["java" "src/main/java"]
  ;  :resource-paths ["src/main/resources"]

  ;; clojure source lib
  (build
    '{:output-dir "out-lib"
      :tasks [[dirs] [clean] [sync-pom] [copy] [jar] [install]]
      :params {:build/lib my/lib1
               :build/version "1.2.3"
               :build/copy-specs [{:from :clj-paths}]}})

  ;; clojure source lib with git version template
  (build
    '{:output-dir "out-lib-git"
      :tasks [[clojure.tools.build.extra/git-version] [dirs] [clean] [sync-pom] [copy] [jar]]
      :params {:git-version/template "0.8.%s"
               :build/lib my/lib2
               :build/copy-specs [{:from :clj-paths}]}})

  ;; java executable jar (no clojure!)
  (build
    '{:output-dir "out-java"
      :tasks [[dirs] [clean] [javac] [sync-pom] [jar]]
      :params {:build/java-paths :java-paths
               :build/javac-opts ["-source" "8" "-target" "8"]
               :build/lib org.clojure/tools.build
               :build/version "0.1.0"
               :build/main-class clojure.tools.build.demo}})

  ;; compiled clojure lib jar w/metadata elided
  (build
    '{:output-dir "out-compiled-lib"
      :tasks [[dirs] [clean] [compile-clj] [copy] [jar]]
      :params {:build/clj-paths :clj-paths
               :build/filter-nses [clojure.tools.build]
               :build/compiler-opts {:elide-meta [:doc :file :line]}
               :build/copy-specs [{:from :resource-paths}]
               :build/lib org.clojure/tools.build
               :build/version "0.1.0"}})

  ;; compiled clojure app jar
  (build
    '{:output-dir "out-compiled-app"
      :tasks [[dirs] [clean] [compile-clj] [copy] [jar]]
      :params {:build/clj-paths :clj-paths
               :build/copy-specs [{:from :resource-paths}]
               :build/lib org.clojure/tools.build
               :build/version "0.1.0"
               :build/main-class clojure.tools.build.demo}})

  ;; uber compiled jar
  (build
    '{:output-dir "out-compiled-uber"
      :tasks [[dirs] [clean] [sync-pom] [compile-clj] [copy] [uber]]
      :params {:build/clj-paths :clj-paths
               :build/copy-specs [{:from :resource-paths}]
               :build/lib my/lib1
               :build/version "1.2.3"
               :build/main-class clojure.tools.build.demo}})

  ;; compiled lib w/classifier
  (build
    '{:output-dir "out-classifier"
      :tasks [[clojure.tools.build.extra/git-version] [dirs] [clean] [sync-pom] [compile-clj] [jar]]
      :params {:build/lib org.clojure/tools.build
               :build/classifier "aot"
               :git-version/template "0.8.%s"
               :build/clj-paths :clj-paths
               :build/filter-nses [clojure.tools.build]}})

  ;; zip
  (build
    '{:output-dir "out-zip"
      :tasks [[clean] [copy] [zip]]
      :params {:build/target-dir "target"
               :build/class-dir "target/classes"
               :build/zip-dir "target/zip"
               :build/copy-to :build/zip-dir
               :build/copy-specs [{:include "README.md"}
                                  {:from :java-paths :include "**/*.java"}]
               :build/zip-name "target/java-source.zip"}})

  ;; process / format
  (build
    '{:output-dir "out-process"
      :tasks [[clean]
              [process {:build/command ["git" "rev-list" "HEAD" "--count"]
                        :build/out> :git/rev}]
              [format-str {:build/template "example-%s.zip"
                           :build/args [:git/rev]
                           :build/out> :build/zip-name}]
              [copy {:build/copy-to :build/zip-dir
                     :build/copy-specs [{:include "README.md"
                                         :replace {"VERSION" :git/rev
                                                   "FOO" "hi there"}}]}]
              [zip]]
      :params {:build/zip-dir "zip"
               :build/target-dir :build/zip-dir}})

  ;; merge data readers
  (let [basis (deps/calc-basis '{:deps {spyscope/spyscope {:mvn/version "0.1.6"}
                                        com.ladderlife/cellophane {:mvn/version "0.3.5"}}
                                 :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                                             "clojars" {:url "https://repo.clojars.org/"}}})]
    ((requiring-resolve 'clojure.tools.build.tasks.clean/clean) basis '{:build/target-dir "out-merge/target"})
    ((requiring-resolve 'clojure.tools.build.tasks.uber/uber) basis
      '{:build/output-dir "out-merge"
        :build/target-dir "target"
        :build/class-dir "target/classes"
        :build/uber-file "target/uber.zip"
        :build/lib test/merge
        :build/version "1.2.3"}))
  )
