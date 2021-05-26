;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.load-basis
  (:require
    [clojure.tools.build.api :as api]
    [clojure.tools.deps.alpha :as deps]))

(defn load-basis
  "Load the project basis (classpath context based on project deps.edn)
  and returns it.

  Options:
    :deps-file - path to deps file, default = deps.edn"
  ([]
   (load-basis nil))
  ([{:keys [deps-file] :or {deps-file "deps.edn"}}]
   (let [{:keys [root-edn project-edn]} (deps/find-edn-maps (api/resolve-path deps-file))
         edns [root-edn project-edn]
         master-edn (deps/merge-edns edns)]
     (deps/calc-basis master-edn))))
