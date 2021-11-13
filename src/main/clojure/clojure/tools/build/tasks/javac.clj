;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.javac
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.tasks.process :as process]
    [clojure.tools.build.util.file :as file])
  (:import
    [java.io File]))

(set! *warn-on-reflection* true)

(defn javac
  "Compile java sources."
  [{:keys [basis javac-opts class-dir src-dirs] :as params}]

  (when (seq src-dirs)
    (let [{:keys [libs]} basis
          class-dir (file/ensure-dir (api/resolve-path class-dir))
          class-dir-path (.getPath class-dir)
          classpath (str/join File/pathSeparator (conj (mapcat :paths (vals libs)) class-dir-path))
          options (concat ["-classpath" classpath "-d" class-dir-path] javac-opts)
          java-files (mapcat #(file/collect-files (api/resolve-path %) :collect (file/suffixes ".java")) src-dirs)
          java-files (map str java-files)

          args          (-> ["javac"
                            ;; "-sourcepath" (str/join ":" src-dirs)
                             ]
                            (into options)
                            (into java-files))]
      (file/ensure-dir class-dir)
      (process/process
       {:command-args args
        :out :capture}))))
