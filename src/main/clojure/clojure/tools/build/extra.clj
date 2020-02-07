(ns clojure.tools.build.extra)

(defn git-version
  [{:keys [params] :as build-info}]
  (let [{:build/keys [version-template]} params
        version (format version-template "123")] ;; use git to determine git distance
    (println "Calculating git version:" version)
    (assoc-in build-info [:build/params :version] version)))

