(ns clojure.tools.build.tasks.test-javac
  (:require
    [clojure.test :refer :all :as test]
    [clojure.java.io :as jio]
    [clojure.tools.build.test-util :refer :all]
    [clojure.tools.build.task.process :as process]))

(deftest test-javac
  (let [out-dir (build-project '{:project-dir "test-data/p1"
                                 :tasks [[javac]]
                                 :params {:build/target-dir "target"
                                          :build/class-dir "target/classes"
                                          :build/jar-file "target/out.jar"
                                          :build/java-paths ["java"]}})]
    (is (true? (.exists (jio/file out-dir "target/classes/foo/Demo1.class"))))
    (is (true? (.exists (jio/file out-dir "target/classes/foo/Demo2.class"))))
    (let [class-path (.getPath (jio/file out-dir "target/classes"))]
      (is (= "Hello" (process/invoke ["java" "-cp" class-path "foo.Demo1"])))
      (is (= "Hello" (process/invoke ["java" "-cp" class-path "foo.Demo2"]))))))

(comment
  (run-tests)
  )