;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.extra)

(defn git-version
  [{:keys [params] :as build-info}]
  (let [{:build/keys [version-template]} params
        version (format version-template "123")] ;; use git to determine git distance
    (println "Calculating git version:" version)
    (assoc-in build-info [:build/params :version] version)))

