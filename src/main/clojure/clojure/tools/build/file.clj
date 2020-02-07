;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.file
  (:require
    [clojure.java.io :as jio])
  (:import
    [java.io File]
    [java.nio.file Path Files LinkOption]))

(set! *warn-on-reflection* true)

(defn delete-path
  "Recursively delete Path, which may be a file or directory"
  [^Path path]
  (when (.exists (.toFile path))
    (when (Files/isDirectory path (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
      (with-open [entries (Files/newDirectoryStream path)]
        (run! delete-path entries)))
    (Files/delete path)))

(defn delete
  "Recursively delete file per clojure.java.io/file"
  [file]
  (delete-path (.toPath (jio/file file))))

(defn ensure-dir
  ^File [dir]
  (let [d (jio/file dir)]
    (if (.exists d)
      d
      (if-let [created (.mkdirs d)]
        d
        (throw (ex-info (str "Can't create directory " dir) {}))))))