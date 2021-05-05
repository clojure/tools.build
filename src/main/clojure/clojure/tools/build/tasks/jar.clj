;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.jar
  (:require
    [clojure.java.io :as jio]
    [clojure.tools.build.task.file :as file]
    [clojure.tools.build.task.zip :as zip])
  (:import
    [java.io File FileOutputStream]
    [java.util.jar Manifest JarOutputStream]))

(defn jar
  [{:build/keys [output-dir compile-dir basis jar-file main] :as params}]
  (let [jar-file (jio/file output-dir jar-file)
        class-dir-file (file/ensure-dir (jio/file output-dir compile-dir))]
    (let [manifest (Manifest.)]
      (zip/fill-manifest! manifest
        (cond->
          {"Manifest-Version" "1.0"
           "Created-By" "org.clojure/tools.build"
           "Build-Jdk-Spec" (System/getProperty "java.specification.version")}
          main (assoc "Main-Class" (str main))))
      (with-open [jos (JarOutputStream. (FileOutputStream. jar-file) manifest)]
        (zip/copy-to-zip jos class-dir-file))))
  params)
