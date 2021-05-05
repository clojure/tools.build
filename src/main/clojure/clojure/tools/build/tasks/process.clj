;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.process
  (:require
    [clojure.tools.build.task.api :as tapi]
    [clojure.tools.build.task.process :as process]))

;; FUTURE: set directory, env vars, out/err handling

(defn process
  [{:build/keys [basis out>] :as params}]
  (let [command (tapi/resolve-param basis params :build/command)
        resolved-command (map #(tapi/maybe-resolve-param basis params %) command)
        out (process/invoke resolved-command)]
    (if out>
      (merge params {out> out})
      params)))
