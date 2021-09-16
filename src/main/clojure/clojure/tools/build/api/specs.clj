(ns clojure.tools.build.api.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::lib qualified-ident?)
(s/def ::path string?)
(s/def ::paths (s/coll-of string?))