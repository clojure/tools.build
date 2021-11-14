;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-install
  (:require
    [clojure.java.io :as jio]
    [clojure.test :as test :refer [deftest is]]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.test-util :refer [with-test-dir *test-dir* project-path]]))

(def test-org (str (gensym)))
(def test-lib (str (gensym)))
(def lib (symbol test-org test-lib))

(deftest test-install
  (let [jar-path "target/output.jar"]
    (with-test-dir "test-data/p1"
      (api/set-project-root! (.getAbsolutePath *test-dir*))
      (api/jar {:class-dir "src"
                :jar-file jar-path})
      (api/install {:basis (api/create-basis {:project "deps.edn"})
                    ;; TODO: why do I need to wrap this in project-path?
                    :jar-file (project-path jar-path)
                    :lib lib
                    :class-dir "src"
                    :version "1.0.0"})
      (let [expected-dir (jio/file (System/getProperty "user.home") ".m2" "repository" (str test-org))
            expected-jar (jio/file expected-dir test-lib "1.0.0" (str test-lib "-1.0.0.jar"))]
        (is (.exists expected-dir))
        (is (.exists expected-jar))
        (api/delete {:path (str expected-dir)}))))

  (comment
    (test/run-tests)
    ))
