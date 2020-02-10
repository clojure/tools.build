;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.file
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str])
  (:import
    [java.io File]
    [java.nio.file Path Files LinkOption]))

(set! *warn-on-reflection* true)

(defn collect-files
  "Recursively collect all paths under path, starting from root.
   Options:
    :dirs - whether to collect directories (default false)
    :collect - function for whether to collect a path (default yes)"
  [^File root & {:keys [dirs collect]
                 :or {dirs false
                      collect (constantly true)}}]
  (when (.exists root)
    (loop [queue (conj (clojure.lang.PersistentQueue/EMPTY) root)
           collected []]
      (let [^File file (peek queue)]
        (if file
          (let [path (.toPath file)
                is-dir (Files/isDirectory path (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
                children (when is-dir (with-open [entries (Files/newDirectoryStream path)]
                                        (mapv #(.toFile ^Path %) entries)))
                collect? (and (if is-dir dirs true) (collect file))]
            (recur (into (pop queue) children) (if collect? (conj collected file) collected)))
          (when (seq collected) collected))))))

(defn suffixes
  "Returns a predicate matching suffixes"
  [& suffixes]
  (apply some-fn
    (map #(fn [^File f] (str/ends-with? (.toString f) ^String %)) suffixes)))

(defn delete
  "Recursively delete file, where file is coerced with clojure.java.io/file"
  [file]
  (run! #(.delete ^File %) (rseq (collect-files (jio/file file) :dirs true))))

(defn ensure-dir
  ^File [dir]
  (let [d (jio/file dir)]
    (if (.exists d)
      d
      (if-let [created (.mkdirs d)]
        d
        (throw (ex-info (str "Can't create directory " dir) {}))))))