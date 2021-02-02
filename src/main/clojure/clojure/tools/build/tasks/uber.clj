;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.uber
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as jio]
    [clojure.pprint :as pprint]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.build.task.api :as tapi]
    [clojure.tools.build.task.file :as file]
    [clojure.tools.build.task.zip :as zip])
  (:import
    [java.io File InputStream FileInputStream BufferedInputStream
             OutputStream FileOutputStream BufferedOutputStream ByteArrayOutputStream]
    [java.nio.file Files]
    [java.util.jar JarInputStream JarOutputStream Manifest]))

(set! *warn-on-reflection* true)

(def ^:private uber-exclusions
  [#"project.clj"
   #"META-INF/.*\.(?:SF|RSA|DSA)"])

(defn- exclude-from-uber?
  [^String path]
  (loop [[re & res] uber-exclusions]
    (if re
      (if (re-matches re path)
        true
        (recur res))
      false)))

(defn- copy-stream!
  "Copy input stream to output stream using buffer.
  Caller is responsible for passing buffered streams and closing streams."
  [^InputStream is ^OutputStream os ^bytes buffer]
  (loop []
    (let [size (.read is buffer)]
      (if (pos? size)
        (do
          (.write os buffer 0 size)
          (recur))
        (.close os)))))

(defn- explode
  [^File lib-file out-dir]
  (if (str/ends-with? (.getPath lib-file) ".jar")
    (let [buffer (byte-array 1024)]
      (with-open [jis (JarInputStream. (BufferedInputStream. (FileInputStream. lib-file)))]
        (loop []
          (when-let [entry (.getNextJarEntry jis)]
            ;(println "entry:" (.getName entry) (.isDirectory entry))
            (let [out-file (jio/file out-dir (.getName entry))]
              (jio/make-parents out-file)
              (when-not (or (.isDirectory entry) (exclude-from-uber? (.getName entry)))
                (if (.exists out-file)
                  ;; conflicting file, resolve
                  (cond
                    (#{"data_readers.clj" "data_readers.cljc"} (.getName out-file))
                    (let [existing-readers (edn/read-string (slurp out-file))
                          baos (ByteArrayOutputStream. 1024)
                          _ (copy-stream! jis baos buffer)
                          append-readers (edn/read-string (.toString baos "UTF-8"))
                          new-readers (merge existing-readers append-readers)]
                      (spit out-file (with-out-str (pprint/pprint new-readers))))

                    :else
                    nil ;; TODO: (println "CONFLICT: " (.getName entry))
                    )
                  (with-open [output (BufferedOutputStream. (FileOutputStream. out-file))]
                    (copy-stream! jis output buffer)
                    (Files/setLastModifiedTime (.toPath out-file) (.getLastModifiedTime entry)))))
              (recur))))))
    (file/copy-contents lib-file out-dir)))

(defn remove-optional
  "Remove optional libs and their transitive dependencies from the lib tree.
  Only remove transitive if all dependents are optional."
  [libs]
  (let [by-opt (group-by (fn [[lib coord]] (boolean (:optional coord))) libs)
        optional (apply conj {} (get by-opt true))]
    (if (seq optional)
      (loop [req (get by-opt false)
             opt optional]
        (let [under-opts (group-by (fn [[lib {:keys [dependents]}]]
                                     (boolean
                                       (and (seq dependents)
                                         (set/subset? (set dependents) (set (keys opt))))))
                           req)
              trans-opt (get under-opts true)]
          (if (seq trans-opt)
            (recur (get under-opts false) (into opt trans-opt))
            (apply conj {} req))))
      libs)))

(defn uber
  [{:keys [libs] :as basis} {:build/keys [output-dir target-dir] :as params}]
  (let [class-dir (jio/file output-dir (tapi/resolve-param basis params :build/class-dir))
        main-class (tapi/resolve-param basis params :build/main-class)
        uber-dir (file/ensure-dir (jio/file output-dir target-dir "uber"))
        uber-file (jio/file output-dir (tapi/resolve-param basis params :build/uber-file))
        manifest (Manifest.)
        lib-paths (conj (->> libs remove-optional vals (mapcat :paths) (map #(jio/file %))) class-dir)]
    (run! #(explode % uber-dir) lib-paths)
    (zip/fill-manifest! manifest
      (cond->
        {"Manifest-Version" "1.0"
         "Created-By" "org.clojure/tools.build"
         "Build-Jdk-Spec" (System/getProperty "java.specification.version")}
        main-class (assoc "Main-Class" (str main-class))
        (.exists (jio/file uber-dir "META-INF" "versions")) (assoc "Multi-Release" "true")))
    (with-open [jos (JarOutputStream. (FileOutputStream. uber-file) manifest)]
      (zip/copy-to-zip jos uber-dir))
    params))