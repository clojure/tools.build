;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.javac
  (:require
    [clojure.string :as str]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.util.file :as file])
  (:import
    [java.io File]
    [javax.tools ToolProvider DiagnosticListener]))

(set! *warn-on-reflection* true)

(defn javac
  [{:keys [basis javac-opts class-dir src-dirs] :as params}]
  (let [{:keys [libs]} basis]
    (when (seq src-dirs)
      (let [class-dir (file/ensure-dir (api/resolve-path class-dir))
            compiler (ToolProvider/getSystemJavaCompiler)
            listener (reify DiagnosticListener (report [_ diag] (println (str diag))))
            file-mgr (.getStandardFileManager compiler listener nil nil)
            class-dir-path (.getPath class-dir)
            classpath (str/join File/pathSeparator (conj (mapcat :paths (vals libs)) class-dir-path))
            options (concat ["-classpath" classpath "-d" class-dir-path] javac-opts)
            java-files (mapcat #(file/collect-files (api/resolve-path %) :collect (file/suffixes ".java")) src-dirs)
            file-objs (.getJavaFileObjectsFromFiles file-mgr java-files)
            task (.getTask compiler nil file-mgr listener options nil file-objs)
            success (.call task)]
        (when-not success
          (throw (ex-info "Java compilation failed" {})))))))
