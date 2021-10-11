;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.process
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.build.api :as api])
  (:import
    [java.io InputStream StringWriter]
    [java.lang ProcessBuilder ProcessBuilder$Redirect]
    [java.util List]))

(defn- copy-stream
  [^InputStream input-stream]
  (let [writer (StringWriter.)]
    (jio/copy input-stream writer)
    (let [s (.toString writer)]
      (when-not (zero? (.length s))
        s))))

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
  [{:keys [command-args dir env out err out-file err-file]
    :or {dir ".", out :inherit, err :inherit} :as opts}]
  (when (not (seq command-args))
    (throw (ex-info "process missing required arg :command-args" opts)))
  (let [pb (ProcessBuilder. ^List command-args)]
    (.directory pb (api/resolve-path (or dir ".")))
    (case out
      :inherit (.redirectOutput pb ProcessBuilder$Redirect/INHERIT)
      :write (.redirectOutput pb (ProcessBuilder$Redirect/to (jio/file (api/resolve-path out-file))))
      :append (.redirectOutput pb (ProcessBuilder$Redirect/appendTo (jio/file (api/resolve-path out-file))))
      :capture (.redirectOutput pb ProcessBuilder$Redirect/PIPE)
      :ignore (.redirectOutput pb ProcessBuilder$Redirect/PIPE))
    (case err
      :inherit (.redirectError pb ProcessBuilder$Redirect/INHERIT)
      :write (.redirectError pb (ProcessBuilder$Redirect/to (jio/file (api/resolve-path err-file))))
      :append (.redirectError pb (ProcessBuilder$Redirect/appendTo (jio/file (api/resolve-path err-file))))
      :capture (.redirectError pb ProcessBuilder$Redirect/PIPE)
      :ignore (.redirectError pb ProcessBuilder$Redirect/PIPE))
    (when env
      (let [pb-env (.environment pb)]
        (run! (fn [[k v]] (.put pb-env k v)) env)))
    (let [proc (.start pb)
          exit (.waitFor proc)
          out-str (when (= out :capture) (copy-stream (.getInputStream proc)))
          err-str (when (= err :capture) (copy-stream (.getErrorStream proc)))]
      (cond-> {:exit exit}
        out-str (assoc :out out-str)
        err-str (assoc :err err-str)))))

(defn java-command
  "Create Java command line args. The classpath will be the combination of
  :cp followed by the classpath from the basis, both are optional.

  Options:
    :java-cmd - Java command, default = \"java\"
    :cp - coll of string classpath entries, used first (if provided)
    :basis - runtime basis used for classpath, used last (if provided)
    :java-opts - coll of string jvm opts
    :main - required, main class symbol
    :main-args - coll of main class args

  Returns:
    :command-args - coll of command arg strings"
  [{:keys [java-cmd cp basis java-opts main main-args]
    :or {java-cmd "java"} :as params}]
  (let [{:keys [classpath]} basis
        cp-entries (concat cp (keys classpath))
        _ (println cp-entries)
        cp-str (->> cp-entries
                 (map #(api/resolve-path %))
                 deps/join-classpath)]
    {:command-args (vec (concat [java-cmd] java-opts ["-cp" cp-str (name main)] main-args))}))