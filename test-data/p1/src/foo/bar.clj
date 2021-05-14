(ns foo.bar
  (:gen-class))

(defn hello [] (println "hello"))

(defn -main
  [& args]
  (hello))
