;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-process
  (:require
    [clojure.test :refer :all]
    [clojure.tools.build.tasks.process :as process]))

(deftest test-need-cp-file
  (let [f #'process/need-cp-file]
    (are [os java length expected]
      (= expected (f os java length))
      "Windows 10" "9.0.1" 10000 true
      "Windows 10" "9.0.1" 5000 false
      "Windows 10" "1.8.0_261" 10000 false
      "Mac OS X" "17" 10000 false)))