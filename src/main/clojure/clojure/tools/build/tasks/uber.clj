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
    [clojure.tools.build.api :as api]
    [clojure.tools.build.util.file :as file]
    [clojure.tools.build.util.zip :as zip])
  (:import
    [java.io File InputStream FileInputStream BufferedInputStream ByteArrayInputStream
             OutputStream FileOutputStream BufferedOutputStream ByteArrayOutputStream]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]
    [java.util.jar JarInputStream JarOutputStream Manifest]))

(set! *warn-on-reflection* true)

(def ^:private uber-exclusions
  [#"project.clj"
   #"META-INF/.*\.(?:SF|RSA|DSA|MF)"])

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

(defn- stream->string
  [^InputStream is]
  (let [baos (ByteArrayOutputStream. 4096)
        _ (copy-stream! is baos (byte-array 4096))]
    (.toString baos "UTF-8")))

(defn- string->stream
  [^String s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

;(fn [{:path        String       ;; path in uber jar, matched by regex
;      :in          InputStream    ;; input stream to incoming file
;      :existing    File     ;; existing file under root-dir
;      :lib         Symbol        ;; lib source
;      :writer      ;; writer function (fn [input-stream path])
;      :entry       Map$Entry
;      }])  ;; return map of {path String|InputStream}

(defn make-writer
  [buffer root-dir ^JarEntry entry]
  (fn [path ^InputStream in {:keys [append] :or {append false}}]
    (let [out-file (jio/file root-dir path)]
      (with-open [output (BufferedOutputStream. (FileOutputStream. out-file ^boolean append))]
        (copy-stream! in output buffer)
        (Files/setLastModifiedTime (.toPath out-file) (.getLastModifiedTime entry))))))

(defn conflict-overwrite
  [{:keys [path in writer]}]
  (writer in path))

(defn- conflict-append
  [{:keys [path in writer]}]
  (writer in path {:append true}))

(defn conflict-data-readers
  [{:keys [path in writer ^File existing]}]
  (let [existing-reader-fns (edn/read-string (slurp existing))
        append-reader-fns (edn/read-string (stream->string in))
        new-reader-fns (merge existing-reader-fns append-reader-fns)
        data-stream (-> new-reader-fns pprint/pprint with-out-str string->stream)]
    (writer data-stream path)))

(defn- conflict-append-dedupe
  [{:keys [] :as params}]
  ;; TODO - dedupe
  (conflict-append params))

(defn- conflict-warn
  [{:keys [path lib]}]
  (println "Conflicting path at" path "from" lib))

(defn- conflict-error
  [{:keys [path lib]}]
  (throw (ex-info (str "Conflicting path at " path " from " lib) {})))

(defn- handle-conflict
  [handlers {:keys [out-dir path buffer] :as state}]
  (let [default-handler (:default handlers)]
    (loop [[[re handler] & hs] (dissoc handlers :default)]
      (if re
        (if (re-matches re path)
          (do
            (doseq [{:keys [out-path out-string out-stream append] :or {append false}} (handler state)]
              (let [out-file (jio/file out-dir out-path ^boolean append)]
                (if out-string
                  (spit! out-file out-string)
                  (copy-stream! out-stream (BufferedInputStream. (FileInputStream. out-file)) buffer))))
            (recur hs)))
        (default-handler state)))))

(defn- explode
  [^File lib-file lib out-dir handlers]
  (if (str/ends-with? (.getPath lib-file) ".jar")
    (let [buffer (byte-array 4096)
          conflict-context {:out-dir out-dir, :buffer buffer, :lib lib}]
      (with-open [jis (JarInputStream. (BufferedInputStream. (FileInputStream. lib-file)))]
        (loop []
          (when-let [entry (.getNextJarEntry jis)]
            ;(println "entry:" (.getName entry) (.isDirectory entry))
            (let [path (.getName entry)
                  out-file (jio/file out-dir path)]
              (jio/make-parents out-file)
              (when-not (or (.isDirectory entry) (exclude-from-uber? path))
                (if (.exists out-file)
                  (handle-conflict handlers
                    (merge conflict-context {:path path, :in jis}))
                  (with-open [output (BufferedOutputStream. (FileOutputStream. out-file))]
                    (copy-stream! jis output buffer)
                    (Files/setLastModifiedTime (.toPath out-file) (.getLastModifiedTime entry)))))
              (recur))))))
    (file/copy-contents lib-file out-dir)))

(defn- remove-optional
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

(defn- built-ins
  [kw]
  (or
    (get {:ignore (fn [_])
          :overwrite conflict-overwrite
          :append conflict-append
          :append-dedupe conflict-append-dedupe
          :data-readers conflict-data-readers
          :warn conflict-warn
          :error conflict-error}
      kw)
    (throw (ex-info (str "Invalid handler: " kw) {}))))

(defn- prep-handler
  "Convert user handler to fn"
  [handler]
  (cond
    (keyword? handler) (built-ins handler)
    (symbol? handler) (requiring-resolve handler)
    (vector? handler) (fn [m] (doseq [h (map prep-handler handler)] (h m)))
    (ifn? handler) handler
    :else (throw (ex-info (str "Invalid handler: " handler) {}))))

(def ^:private default-handlers
  {"^data_readers.clj[cs]?$" :data_readers
   "^META-INF/services/" :append
   "(?i)^(META-INF/)?(COPYRIGHT|NOTICE|LICENSE)(\.(txt|md))?$" :append-dedupe
   :default :ignore})

(defn- prep-handlers
  "Transform user handler map into a map of re->fn"
  [handlers]
  (reduce-kv
    (fn [m pattern handler]
      (if (= pattern :default))
      (assoc m (if (= pattern :default) :default (re-pattern pattern))
               (prep-handler handler)))
    {} (merge default-handlers handlers)))

(defn uber
  [{mf-attrs :manifest, :keys [basis class-dir uber-file main conflict-handlers] :as params}]
  (let [working-dir (.toFile (Files/createTempDirectory "uber" (into-array FileAttribute [])))
        hs (prep-handlers conflict-handlers)]
    (try
      (let [{:keys [libs]} basis
            compile-dir (api/resolve-path class-dir)
            manifest (Manifest.)
            uber-file (api/resolve-path uber-file)
            mf-attr-strs (reduce-kv (fn [m k v] (assoc m (str k) (str v))) nil mf-attrs)]
        (run! (fn [[lib paths]]
                (doseq [path paths]
                  (explode (jio/file path) lib working-dir hs)))
          (assoc (remove-optional libs) nil compile-dir))
        (zip/fill-manifest! manifest
          (merge
            (cond->
              {"Manifest-Version" "1.0"
               "Created-By" "org.clojure/tools.build"
               "Build-Jdk-Spec" (System/getProperty "java.specification.version")}
              main (assoc "Main-Class" (str/replace (str main) \- \_))
              (.exists (jio/file working-dir "META-INF" "versions")) (assoc "Multi-Release" "true"))
            mf-attr-strs))
        (file/ensure-dir (.getParent uber-file))
        (with-open [jos (JarOutputStream. (FileOutputStream. uber-file) manifest)]
          (zip/copy-to-zip jos working-dir)))
      (finally
        (file/delete working-dir)))))