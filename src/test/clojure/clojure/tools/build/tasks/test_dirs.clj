;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-dirs
  (:require
    [clojure.test :refer :all :as test]
    [clojure.java.io :as jio]
    [clojure.tools.build.test-util :refer :all]))

(defn spy-params
  [_basis {:build/keys [append-to] :as params}]
  (reset! append-to params))

(deftest test-dirs
  (binding [])
  (let [capture (atom nil)
        out-dir (build-project {:project-dir "test-data/p1"
                                :tasks '[[dirs] [clojure.tools.build.tasks.test-dirs/spy-params]]
                                :params {:build/append-to capture
                                         :build/lib 'my/p1
                                         :build/version "1.2.3"}})
        params @capture]
    (is (= (select-keys @capture [:build/pom-dir :build/jar-file :build/uber-file :build/class-dir :build/target-dir])
          {:build/pom-dir "target/classes/META-INF/maven/my/p1"
           :build/jar-file "target/p1-1.2.3.jar"
           :build/uber-file "target/p1-1.2.3-standalone.jar"
           :build/class-dir "target/classes"
           :build/target-dir "target"}))))

(comment
  (run-tests)
  )