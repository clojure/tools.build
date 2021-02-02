;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.compile-clj
  (:require
    [clojure.java.io :as jio]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.build.task.api :as tapi]
    [clojure.tools.build.task.file :as file]
    [clojure.tools.build.task.process :as process]
    [clojure.tools.namespace.find :as find])
  (:import
    [java.io File]))

(set! *warn-on-reflection* true)

(defn- write-compile-script
  ^File [^File target-dir ^File compile-dir nses compiler-opts]
  (let [script-file (jio/file target-dir (str (.getName compile-dir) ".clj"))
        script `(binding [~'*compile-path* ~(str compile-dir)
                          ~'*compiler-options* ~compiler-opts]
                  ~@(map (fn [n] `(~'compile '~n)) nses)
                  (System/exit 0))]
    (spit script-file (with-out-str (pprint/pprint script)))
    script-file))

(defn- ns->path
  [ns-sym]
  (str/replace (clojure.lang.Compiler/munge (str ns-sym)) \. \/))

(defn compile-clj
  [{:keys [classpath] :as basis} {:build/keys [project-dir output-dir] :as params}]
  (let [clj-paths (tapi/resolve-param basis params :build/clj-paths)
        target-dir (file/ensure-dir (jio/file output-dir (tapi/resolve-param basis params :build/target-dir)))
        class-dir (file/ensure-dir (jio/file output-dir (tapi/resolve-param basis params :build/class-dir)))
        compiler-opts (tapi/resolve-param basis params :build/compiler-opts)
        ns-compile (tapi/resolve-param basis params :build/ns-compile)
        filter-nses (tapi/resolve-param basis params :build/filter-nses)
        srcs (map #(tapi/maybe-resolve-param basis params %) clj-paths)
        nses (or ns-compile
               (mapcat #(find/find-namespaces-in-dir (jio/file project-dir %) find/clj) srcs))
        compile-dir (file/ensure-dir (jio/file target-dir "compile-clj"))
        compile-script (write-compile-script target-dir compile-dir nses compiler-opts)
        cp-str (-> classpath keys (conj compile-dir (.getPath class-dir)) deps/join-classpath)
        args ["java" "-cp" cp-str "clojure.main" (.getCanonicalPath compile-script)]
        exit (process/exec args)]
    (if (zero? exit)
      (if (seq filter-nses)
        (file/copy-contents compile-dir class-dir (map ns->path filter-nses))
        (file/copy-contents compile-dir class-dir))
      (throw (ex-info "Clojure compilation failed" {})))
    params))
