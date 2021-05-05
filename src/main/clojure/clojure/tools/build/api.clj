(ns clojure.tools.build.api
  (:require
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.build.task.process :as process]))

;; Basis

(defn load-basis
  []
  (let [{:keys [root-edn project-edn]} (deps/find-edn-maps)
        edns [root-edn project-edn]
        master-edn (deps/merge-edns edns)]
    (deps/calc-basis master-edn)))

;; Helpers

(defn git-version
  [template]
  (let [git-version (process/invoke ["git" "rev-list" "HEAD" "--count"])]
    (format template git-version)))

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

