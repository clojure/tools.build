;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.task.api)

(defn resolve-param
  "Resolve task param, flow param, or alias. First tries to resolve key
  as param or flow, repeatedly while finding keywords. Next tries to resolve
  alias if still a keyword. Returns nil if not resolved."
  ([basis params key default-key]
   (if (contains? params key)
     (resolve-param basis params key)
     (resolve-param basis params default-key)))
  ([basis params key]
   (loop [k key]
     (let [v (get params k)]
       (cond
         (keyword? v) (recur v)
         (nil? v) (get-in basis [:aliases k])
         :else v)))))

(defn maybe-resolve-param
  "Resolve task param but if not found, return possible-key instead"
  [basis params possible-key]
  (or (resolve-param basis params possible-key) possible-key))
