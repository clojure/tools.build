;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.clean
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.build.task.api :as tapi]
    [clojure.tools.build.task.file :as file]))

(defn clean
  [basis {:build/keys [output-dir] :as params}]
  (let [target-dir (tapi/resolve-param basis params :build/target-dir)
        target-dir-file (jio/file output-dir target-dir)]
    (file/delete target-dir-file)))