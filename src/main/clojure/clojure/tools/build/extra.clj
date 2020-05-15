;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.extra
  (:require
    [clojure.tools.build :as build]
    [clojure.tools.build.process :as process]))

(defn git-version
  [basis params]
  (let [version-template (build/resolve-param basis params :git-version/template)
        flow-key (:git-version/version> params)
        git-version (process/invoke ["git" "rev-list" "HEAD" "--count"])
        version (format version-template git-version)]
    {flow-key version}))

(comment
  (git-version nil #:git-version{:template "0.1.%s" :version> :flow/version})
  )