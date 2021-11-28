(ns require-deps
  (:require [babashka.deps :as deps]
            [babashka.fs :as fs]
            [babashka.pods :as pods]
            [clojure.string :as str]))

(def windows? (str/starts-with?
               (System/getProperty "os.name")
               "Windows"))

(def native-executable
  (if windows?
    "tools-deps-native.exe"
    (if (fs/exists? "./tools-deps-native")
      "./tools-deps-native"
      "tools-deps-native")))

(pods/load-pod native-executable)

(deps/add-deps
 '{:deps {borkdude/spartan.spec
          {:git/url "https://github.com/borkdude/spartan.spec"
           :sha     "12947185b4f8b8ff8ee3bc0f19c98dbde54d4c90"}}})

(require 'spartan.spec)
