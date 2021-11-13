(ns require-deps
  (:require [babashka.deps :as deps]
            [babashka.pods :as pods]))

(pods/load-pod "tools-deps-native")

(deps/add-deps
 '{:deps {borkdude/spartan.spec
          {:git/url "https://github.com/borkdude/spartan.spec"
           :sha     "12947185b4f8b8ff8ee3bc0f19c98dbde54d4c90"}}})

(require 'spartan.spec)
