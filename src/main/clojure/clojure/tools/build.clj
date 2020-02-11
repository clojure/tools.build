;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build
 (:require
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as reader]))

(defn load-deps
  "Load deps.edn file and produce merged deps map"
  ([]
    (load-deps "deps.edn"))
  ([deps-file]
   (let [install-deps (reader/install-deps)
         user-dep-loc (jio/file (reader/user-deps-location))
         user-deps (when (.exists user-dep-loc) (reader/slurp-deps user-dep-loc))
         project-dep-loc (jio/file deps-file)
         project-deps (when (.exists project-dep-loc) (reader/slurp-deps project-dep-loc))]
     (->> [install-deps user-deps project-deps] (remove nil?) reader/merge-deps))))

(defn resolve-deps
  "Resolve deps.edn and create lib-map and aliases"
  [deps & {:keys [resolve-src]}]
  (let [resolve-args (if (keyword? resolve-src)
                       (-> deps :aliases resolve-src)
                       resolve-src)
        lib-map (deps/resolve-deps deps resolve-args nil)]
    {:lib-map lib-map
     :aliases (:aliases deps)}))

(def default-params
  "Build param defaults"
  {:build/target-dir "target"
   :build/clj-paths ["src"]
   :build/java-paths ["java" "src/main/java"]
   :build/resource-dirs ["resources"]
   :build/src-pom "pom.xml"})

(defn build-params
  [build-info param-src]
  "Load build-params from param-src and merge into build-info.
  param-src can either be a deps alias or a map"
  (let [resolved-params (if (keyword? param-src)
                          (-> build-info :aliases param-src)
                          param-src)]
    (update-in build-info [:params] merge resolved-params)))

;(defn build-info
;  "Construct an initial build info. Optional kwargs:
;    :deps Path to deps.edn file to use (\"deps.edn\" by default)
;    :resolve Alias in deps.edn with resolve-deps args or a map of that data
;    :params Aliases in deps.edn with initial build params OR param maps to be merged"
;  [& {:keys [deps resolve params]
;      :or {deps "deps.edn"}}]
;  (let [install-deps (reader/install-deps)
;        user-dep-loc (jio/file (reader/user-deps-location))
;        user-deps (when (.exists user-dep-loc) (reader/slurp-deps user-dep-loc))
;        project-dep-loc (jio/file deps)
;        project-deps (when (.exists project-dep-loc) (reader/slurp-deps project-dep-loc))
;        deps-map (->> [install-deps user-deps project-deps] (remove nil?) reader/merge-deps)
;        resolve-args (look-up deps-map resolve)
;        lib-map (deps/resolve-deps deps-map nil nil)
;        merge-params (apply merge defaults (map #(look-up deps-map %) params))]
;    {:lib-map lib-map
;     :aliases (:aliases deps-map)
;     :params merge-params}))

(comment
  (require '[clojure.tools.build.tasks :refer :all])

  ;; basic clojure lib build
  (-> (resolve-deps (load-deps))
    (build-params default-params)
    (build-params :build-info)
    clean sync-pom jar end)

  ;; javac, executable jar
  (-> (resolve-deps (load-deps))
    (build-params default-params)
    (build-params :build-info)
    (build-params {:build/main-class 'foo.Demo1})
    clean javac jar end)

  ;; aot app jar
  (-> (resolve-deps (load-deps))
    (build-params default-params)
    (build-params :build-info)
    (build-params {:build/main-class 'clojure.tools.build.demo})
    clean aot jar end)

  ;; uber jar
  (-> (resolve-deps (load-deps))
    (build-params default-params)
    (build-params :build-info)
    clean sync-pom jar uber end)
  )
