(ns require-deps
  (:require
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
