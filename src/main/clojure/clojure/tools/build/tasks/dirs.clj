;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.dirs
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.build.task.api :as tapi]))

(defn dirs
  [basis params]
  (let [lib (tapi/resolve-param basis params :build/lib)
        group-id (namespace lib)
        artifact-id (name lib)
        classifier (tapi/resolve-param basis params :build/classifier)
        version (tapi/resolve-param basis params :build/version)
        class-dir (jio/file "target" "classes")
        pom-dir (jio/file class-dir "META-INF" "maven" group-id artifact-id)
        jar-base (str artifact-id "-" version (if classifier (str "-" classifier) ""))
        jar-file (jio/file "target" (str jar-base ".jar"))
        uber-jar-file (jio/file "target" (str jar-base "-standalone.jar"))]
    (merge params
      {:build/target-dir "target"
       :build/class-dir (.getPath class-dir)
       :build/pom-dir (.getPath pom-dir)
       :build/jar-file (.getPath jar-file)
       :build/uber-file (.getPath uber-jar-file)})))
