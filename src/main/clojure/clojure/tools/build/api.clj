(ns clojure.tools.build.api
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.build.task.process :as process])
  (:import
    [java.io File]))

;; Project dir, defaults to current directory

(def ^:dynamic *project-root* ".")

(defn set-project-root!
  "Set project root dir (default is \".\")"
  [root]
  (alter-var-root #'*project-root* (constantly root)))

(defn resolve-path
  "If path is absolute or root-path is nil, return path, otherwise
  resolve path under root-path."
  ^File [path]
  (let [path-file (jio/file path)]
    (if (.isAbsolute path-file)
      ;; absolute, ignore root
      path-file
      ;; relative to *project-root*
      (jio/file *project-root* path-file))))

;; Basis

(defn load-basis
  ([]
   (load-basis nil))
  ([{:keys [deps-file] :or {deps-file "deps.edn"}}]
   (let [{:keys [root-edn project-edn]} (deps/find-edn-maps (resolve-path deps-file))
         edns [root-edn project-edn]
         master-edn (deps/merge-edns edns)]
     (deps/calc-basis master-edn))))

;; Helpers

(defn git-count-revs
  [{:keys [dir]}]
  (process/invoke ["git" "rev-list" "HEAD" "--count"]))

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

