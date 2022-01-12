;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.install
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.deps.alpha.util.maven :as mvn]
    [clojure.tools.build.api :as api])
  (:import
    [org.eclipse.aether.artifact DefaultArtifact]
    [org.eclipse.aether.installation InstallRequest]))

(set! *warn-on-reflection* true)

(defn install
  [{:keys [basis lib classifier version jar-file class-dir] :as params}]
  (let [{:mvn/keys [local-repo]} basis
        group-id (namespace lib)
        artifact-id (name lib)
        jar-file-file (api/resolve-path jar-file)
        pom-dir (jio/file (api/resolve-path class-dir) "META-INF" "maven" group-id artifact-id)
        pom (jio/file pom-dir "pom.xml")
        system (mvn/make-system)
        session (mvn/make-session system (or local-repo @mvn/cached-local-repo))
        jar-artifact (.setFile (DefaultArtifact. group-id artifact-id classifier "jar" version) jar-file-file)
        artifacts (cond-> [jar-artifact]
                    (and pom-dir (.exists pom)) (conj (.setFile (DefaultArtifact. group-id artifact-id classifier "pom" version) pom)))
        install-request (.setArtifacts (InstallRequest.) artifacts)]
    (.install system session install-request)
    nil))
