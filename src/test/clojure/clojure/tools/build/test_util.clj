(ns clojure.tools.build.test-util
  (:require
    [clojure.java.io :as jio]
    [clojure.test :as test]
    [clojure.tools.build.task.file :as file]
    [clojure.tools.build :as build])
  (:import
    [java.io File]))

(def ^:dynamic ^File *test-dir*)

(defmacro with-test-dir
  [& body]
  `(let [name# (-> test/*testing-vars* last symbol str)
         dir# (jio/file "test-out" name#)]
     (file/delete dir#)
     (.mkdirs dir#)
     (binding [*test-dir* dir#]
       ~@body)))

(defn build-project
  [build-config]
  (with-test-dir
    (build/build (assoc build-config :output-dir (.getPath *test-dir*)))
    *test-dir*))

(defn submap?
  "Is m1 a subset of m2?"
  [m1 m2]
  (if (and (map? m1) (map? m2))
    (every? (fn [[k v]] (and (contains? m2 k)
                          (submap? v (get m2 k))))
      m1)
    (= m1 m2)))