;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.copy
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.util.file :as file])
  (:import
    [java.io File]
    [java.nio.file FileSystems FileVisitor FileVisitResult Files Path]))

(set! *warn-on-reflection* true)

;; copy spec:
;;    :from (coll of dirs), default = ["."]
;;    ;include (glob), default = "**"
;;    :replace (map of replacements) - performed while copying

(defn- match-paths
  "Match glob to paths under root and return a collection of Path objects"
  [^File root glob]
  (let [root-path (.toPath root)
        matcher (.getPathMatcher (FileSystems/getDefault) (str "glob:" glob))
        paths (volatile! [])
        visitor (reify FileVisitor
                  (visitFile [_ path attrs]
                    (when (.matches matcher (.relativize root-path ^Path path))
                      (vswap! paths conj path))
                    FileVisitResult/CONTINUE)
                  (visitFileFailed [_ path ex] FileVisitResult/CONTINUE)
                  (preVisitDirectory [_ _ _] FileVisitResult/CONTINUE)
                  (postVisitDirectory [_ _ _] FileVisitResult/CONTINUE))]
    (Files/walkFileTree root-path visitor)
    @paths))

(def default-ignores
  [".*~$"
   "^#.*#$"
   "^\\.#.*"
   "^.DS_Store$"])

(defn ignore? [name ignore-regexes]
  (boolean (some #(re-matches % name) ignore-regexes)))

(defn copy
  [{:keys [target-dir src-dirs include replace ignores]
    :or {include "**", ignores default-ignores} :as params}]
  (let [to-path (.toPath (file/ensure-dir (api/resolve-path target-dir)))
        ignore-regexes (map re-pattern ignores)]
    (doseq [dir src-dirs]
      ;(println "from" dir)
      (let [from-file (api/resolve-path dir)
            paths (match-paths from-file include)]
        (doseq [^Path path paths]
          (let [path-file (.toFile path)
                target-file (.toFile (.resolve to-path (.relativize (.toPath from-file) path)))]
            (when-not (ignore? (.getName path-file) ignore-regexes)
              ;(println "copying" (.toString path-file) (.toString target-file) (boolean (not (empty? replace))))
              (if (empty? replace)
                (file/copy-file path-file target-file)
                (let [contents (slurp path-file)
                      replaced (reduce (fn [s [find replace]] (str/replace s find replace))
                                 contents replace)]
                  (file/ensure-file target-file replaced :append false))))))))))
