(ns clojure.tools.build.util.pod
  (:require [clojure.string :as str]))

(def windows? (str/starts-with?
               (System/getProperty "os.name")
               "Windows"))

(require '[babashka.fs :as fs]
         '[babashka.pods :as pods])

(def native-executable
  (if windows?
    "tools-deps-native.exe"
    "./tools-deps-native"))

(when (fs/exists? native-executable)
  (pods/load-pod native-executable))

(or (try (requiring-resolve 'clojure.tools.deps/create-basis)
         (catch Exception _ nil)) ;; pod is loaded via bb.edn
    (pods/load-pod 'org.babashka/tools-deps-native "0.1.0"))
