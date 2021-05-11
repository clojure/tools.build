;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.zip
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.build.task.api :as tapi]
    [clojure.tools.build.task.file :as file]
    [clojure.tools.build.task.zip :as zip])
  (:import
    [java.io FileOutputStream]
    [java.util.zip ZipOutputStream]))

(set! *warn-on-reflection* true)

(defn zip
  [{:build/keys [output-dir zip-paths zip-file] :as params}]
  (let [zip-file (jio/file output-dir zip-file)]
    (with-open [zos (ZipOutputStream. (FileOutputStream. zip-file))]
      (doseq [zpath zip-paths]
        (let [zip-from (file/ensure-dir (jio/file output-dir zpath))]
          (println "Zipping from" (.getPath zip-from) "to" (.getPath zip-file))
          (zip/copy-to-zip zos zip-from))))
    params))
