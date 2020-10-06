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
    [clojure.string :as str]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.dir :as dir]
    [clojure.tools.build.task.specs]
    [clojure.spec.alpha :as s])
  (:import
    [java.io File]
    [clojure.lang ExceptionInfo]))

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

(defn- check-params
  [task-sym param-data]
  (let [unqualified-params (filter #(not (qualified-keyword? %)) (keys param-data))]
    (if (seq unqualified-params)
      (throw (ex-info (str "Invalid parameter names, should be qualified keys: " (pr-str unqualified-params)) {}))))

  (loop [[[param val] & more-params] param-data
         errs []]
    (if param
      (cond
        ;; alias, skip
        (keyword? val) (recur more-params errs)

        ;; validate to spec
        (and (s/get-spec param) (not (s/valid? param val)))
        (recur more-params (conj errs {:param param :val val :explain (s/explain-str param val)}))

        ;; no spec
        :else (recur more-params errs))
      (when (seq errs)
        (throw (ex-info (str/join (System/lineSeparator)
                          (cons (str "Invalid params running task `" task-sym "`:")
                            (map (fn [{:keys [param val]}]
                                   (str "  " param ": got " (pr-str val) ", expected: " (pr-str (s/describe param))))
                              errs)))
                 param-data))))))

(defn build
  "Executes a project build consisting of tasks using shared parameters.

    :project-dir (optional) - path to project root, should include deps.edn file (default = current directory),
                              used to form the project basis
    :output-dir (optional) - path to output root (default = project-dir)
    :tasks (required) - coll of task steps in the form [task-sym task-params]
                        task-sym (required) - unqualified for built-in tasks, otherwise should resolve to a var
                        task-params (optional) - map of parameters overriding shared params
    :params (optional) - shared params passed to all tasks or an alias referring to them

   Build steps:
     Load basis from project-dir
     Load build params from either a map or an alias
     Run tasks in order, each task is passed the basis and a merge of build params and task-specific params
     Output files and dirs relative to output-dir"
  [{:keys [project-dir output-dir tasks params verbose]}]
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
    (try
      (reduce
        (fn [run-params [task-sym task-params]]
          (log verbose)
          (log verbose "Running" task-sym)
          (let [begin (System/currentTimeMillis)
                task-fn (resolve-task task-sym)
                task-params (if (keyword? task-params) (get-in basis [:aliases task-params]) task-params)
                param-data (merge run-params task-params)
                _ (log-map verbose param-data)
                _ (check-params task-sym param-data)
                res (task-fn basis param-data)
                end (System/currentTimeMillis)]
            (println "Ran" task-sym "in" (- end begin) "ms")
            (merge run-params res)))
        default-params
        tasks)
      (println "Done!")
      (catch ExceptionInfo e
        (println (.getMessage e))))))

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
               :build/clj-paths :clj-paths
               :build/copy-specs [{:from :clj-paths}]}})

  ;; clojure source lib, no pom
  (build
    '{:output-dir "out-lib-pom"
      :tasks [[dirs] [clean] [sync-pom] [copy] [jar] [install]]
      :params {:build/lib my/lib1
               :build/version "1.2.3"
               :build/src-pom "no.xml"
               :build/clj-paths ["src/main/clojure"]
               :build/resource-paths ["src/main/resources" "src/main/extra"]
               :build/copy-specs [{:from :clj-paths}]}})

  ;; clojure source lib with git version template
  (build
    '{:output-dir "out-lib-git"
      :tasks [[git-version] [dirs] [clean] [sync-pom] [copy] [jar]]
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
      :tasks [[git-version] [dirs] [clean] [sync-pom] [compile-clj] [jar]]
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
