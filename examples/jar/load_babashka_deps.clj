(ns load-babashka-deps)

(require '[babashka.pods :as pods])
;; Load tools-deps-native pod which defines clojure.tools.deps.alpha.
;; This assumes the binar tools-deps-native is on your PATH
;; You can change the call to load from an absolute or relative path instead.
(pods/load-pod "tools-deps-native")

(require '[spartan.spec]) ;; defines clojure.spec.alpha
