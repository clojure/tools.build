(ns clojure.tools.build.api
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str])
  (:import
    [java.io File]))

(set! *warn-on-reflection* true)

;; Project root dir, defaults to current directory

(def ^:dynamic *project-root*
  "Project root path, defaults to current directory.
  Use `resolve-path` to resolve relative paths in terms of the *project-root*.
  Use `set-project-root!` to override the default for all tasks."
  ".")

(defn set-project-root!
  "Set project root dir (default is \".\")"
  [root]
  (alter-var-root #'*project-root* (constantly root)))

(defn resolve-path
  "If path is absolute or root-path is nil then return path,
  otherwise resolve path under root-path."
  ^File [path]
  (let [path-file (jio/file path)]
    (if (.isAbsolute path-file)
      ;; absolute, ignore root
      path-file
      ;; relative to *project-root*
      (jio/file *project-root* path-file))))

;; Basis


(defn load-basis
  "Load the project basis (classpath context based on project deps.edn)
  and returns it.

  Options:
    :deps-file - path to deps file, default = deps.edn"
  ([]
   ((requiring-resolve 'clojure.tools.build.tasks.load-basis/load-basis)))
  ([params]
   ((requiring-resolve 'clojure.tools.build.tasks.load-basis/load-basis) params)))

(defn process
  "Exec the command made from command-args, redirect out and err as directed,
  and return {:exit exit-code, :out captured-out, :err captured-err}

  Options:
    :command-args - required, coll of string args
    :dir - directory to run the command from, default current directory
    :out - one of :inherit :capture :write :append :ignore
    :err - one of :inherit :capture :write :append :ignore
    :out-file - file path to write if :out is :write or :append
    :err-file - file path to write if :err is :write or :append
    :env - map of environment variables to set

  The :out and :err input flags take one of the following options:
    :inherit - inherit the stream and write the subprocess io to this process's stream (default)
    :capture - capture the stream to a string and return it
    :write - write to :out-file or :err-file
    :append - append to :out-file or :err-file
    :ignore - ignore the stream"
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.process/process) params))

(defn git-count-revs
  "Shells out to git to count the number of commits on this branch:
    git rev-list HEAD --count

  Options:
    :dir - dir to invoke this command from, by default current directory"
  [{:keys [dir] :or {dir "."}}]
  (-> {:command-args ["git" "rev-list" "HEAD" "--count"]
       :dir (.getPath (resolve-path dir))
       :out :capture}
    process
    :out
    str/trim))

;; Tasks

(defn clean
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.clean/clean) params))

(defn compile-clj
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.compile-clj/compile-clj) params))

(defn copy
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.copy/copy) params))

(defn install
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.install/install) params))

(defn javac
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.javac/javac) params))

(defn jar
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.jar/jar) params))

(defn sync-pom
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.sync-pom/sync-pom) params))

(defn uber
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.uber/uber) params))

(defn zip
  [params]
  ((requiring-resolve 'clojure.tools.build.tasks.zip/zip) params))

