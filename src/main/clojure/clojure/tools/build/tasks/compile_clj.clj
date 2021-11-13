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
    [clojure.tools.build.api :as api]
    [clojure.tools.build.util.file :as file]
    [clojure.tools.build.tasks.process :as process]
    [clojure.tools.namespace.find :as find]
    [clojure.tools.namespace.dependency :as dependency]
    [clojure.tools.namespace.parse :as parse])
  (:import
    [java.io File]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]))

(set! *warn-on-reflection* true)

(defn- write-compile-script!
  ^File [^File script-file ^File compile-dir nses compiler-opts]
  (let [script `(binding [~'*compile-path* ~(str compile-dir)
                          ~'*compiler-options* ~compiler-opts]
                  ~@(map (fn [n] `(~'compile '~n)) nses)
                  (System/exit 0))]
    (spit script-file (with-out-str (pprint/pprint script)))))

(defn- ns->path
  [ns-sym]
  (str/replace (munge (str ns-sym)) \. \/))

(defn- nses-in-bfs
  [dirs]
  (mapcat #(find/find-namespaces-in-dir (api/resolve-path %) find/clj) dirs))

(defn- nses-in-topo
  [dirs]
  (let [ns-decls (mapcat #(find/find-ns-decls-in-dir (api/resolve-path %)) dirs)
        ns-candidates (set (map parse/name-from-ns-decl ns-decls))
        graph (reduce
                (fn [graph decl]
                  (let [sym (parse/name-from-ns-decl decl)]
                    (reduce
                      (fn [graph dep] (dependency/depend graph sym dep))
                      graph
                      (parse/deps-from-ns-decl decl))))
                (dependency/graph)
                ns-decls)]
    (->> graph
      dependency/topo-sort
      (filter ns-candidates) ;; only keep stuff in these dirs
      (concat ns-candidates) ;; but make sure everything is in there at least once
      distinct)))

(defn compile-clj
  [{:keys [basis src-dirs compile-opts ns-compile filter-nses class-dir sort] :as params
    :or {sort :topo}}]
  (let [working-dir (.toFile (Files/createTempDirectory "compile-clj" (into-array FileAttribute [])))]
    (let [compile-dir-file (file/ensure-dir (api/resolve-path class-dir))
          nses (cond
                 (seq ns-compile) ns-compile
                 (= sort :topo) (nses-in-topo src-dirs)
                 (= sort :bfs) (nses-in-bfs src-dirs)
                 :else (throw (ex-info "Missing :ns-compile or :sort order in compile-clj task" {})))
          working-compile-dir (file/ensure-dir (jio/file working-dir "compile-clj"))
          compile-script (jio/file working-dir "compile.clj")
          _ (write-compile-script! compile-script working-compile-dir nses compile-opts)
          process-args (process/java-command (merge
                                               (select-keys params [:java-cmd :java-opts :use-cp-file])
                                               {:cp [(.getPath working-compile-dir) (.getPath compile-dir-file)]
                                                :basis basis
                                                :main 'clojure.main
                                                :main-args [(.getCanonicalPath compile-script)]}))
          _ (spit (jio/file working-dir "compile.args") (str/join " " (:command-args process-args)))
          exit (:exit (process/process process-args))]
      (if (zero? exit)
        (do
          (if (seq filter-nses)
            (file/copy-contents working-compile-dir compile-dir-file (map ns->path filter-nses))
            (file/copy-contents working-compile-dir compile-dir-file))
          ;; only delete on success, otherwise leave the evidence!
          (file/delete working-dir))
        (throw (ex-info "Clojure compilation failed" {}))))))
