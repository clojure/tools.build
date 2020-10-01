(ns clojure.tools.build.task.specs
  (:require [clojure.spec.alpha :as s]))

(s/def :build/project-dir string?)
(s/def :build/output-dir string?)
(s/def :build/target-dir string?)
(s/def :build/class-dir string?)
(s/def :build/pom-dir string?)
(s/def :build/jar-file string?)
(s/def :build/uber-file string?)

(s/def :build/clj-paths (s/coll-of string?))
(s/def :build/java-paths (s/coll-of string?))

(s/def :build/lib qualified-symbol?)
(s/def :build/classifier string?)
(s/def :build/version string?)
(s/def :build/main-class simple-symbol?)
(s/def :build/compiler-opts (s/map-of keyword? any?))
(s/def :build/ns-compile (s/coll-of simple-symbol?))
(s/def :build/filter-nses (s/coll-of simple-symbol?))
(s/def :build/javac-opts (s/coll-of string?))

(s/def :build.copy/from any?) ;; TODO: alias or dir or coll of dirs
(s/def :build.copy/include any?) ;; TODO: alias or glob or coll of globs
(s/def :build.copy/replace (s/map-of string? (s/or :alias keyword? :text string?)))
(s/def :build/copy-spec (s/keys :opt-un [:build.copy/from :build.copy/include :build.copy/replace]))
(s/def :build/copy-specs (s/coll-of :build/copy-spec))
(s/def :build/copy-to (s/coll-of :build/copy-spec))