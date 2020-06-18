(ns clojure.tools.build.task.process
  (:require
    [clojure.string :as str])
  (:import
    [java.lang ProcessBuilder ProcessBuilder$Redirect]
    [java.io File]
    [java.util List]))

(set! *warn-on-reflection* true)

(defn exec
  "Exec the command in the command-args coll. Redirect stdout/stderr to this process,
  wait for the process to complete, and return the exit code"
  [command-args]
  (let [proc-builder (doto (ProcessBuilder. ^List command-args)
                       (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                       (.redirectError ProcessBuilder$Redirect/INHERIT))
        proc (.start proc-builder)]
    (.waitFor proc)))

(defn invoke
  "Exec the command in the command-args coll. Redirect stderr to this process,
  capture stdout, and return it as a string"
  [command-args]
  (let [f ^File (doto (File/createTempFile "out-" nil) (.deleteOnExit))
        proc-builder (doto (ProcessBuilder. ^List command-args)
                       (.redirectOutput f)
                       (.redirectError ProcessBuilder$Redirect/INHERIT))
        proc (.start proc-builder)]
    (.waitFor proc)
    (-> f slurp str/trim)))

(comment
  (invoke ["ls" "-l"])
  )
