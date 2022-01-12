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
    [java.io File InputStream FileInputStream BufferedInputStream
             OutputStream FileOutputStream BufferedOutputStream ByteArrayOutputStream]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]
    [java.util.jar JarEntry JarInputStream JarOutputStream Manifest]))

(set! *warn-on-reflection* true)

(def ^:private uber-exclusions
  [#"project.clj"
   #"META-INF/.*\.(?:SF|RSA|DSA|MF)"
   #"module-info\.class"
   #"(.*/)?\.DS_Store"
   #"(.*/)?\.keep"
   #".*\.pom"
   #"(?i)META-INF/(?:INDEX\.LIST|DEPENDENCIES)(?:\.txt)?"])

(defn- exclude-from-uber?
  [exclude-patterns ^String path]
  (loop [[re & res] exclude-patterns]
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

(defn conflict-overwrite
  [{:keys [path in]}]
  {:write {path {:stream in}}})

(defn- conflict-append
  [{:keys [path in]}]
  {:write {path {:string (str "\n" (stream->string in)), :append true}}})

(defn- conflict-append-dedupe
  [{:keys [path in ^File existing state] :as _params}]
  (let [existing-content (slurp existing)
        existing-lower (str/lower-case existing-content)
        new-content (stream->string in)
        new-content-lower (str/lower-case new-content)
        seen (or (get-in state [:append-dedupe path]) #{existing-lower})]
    (if (contains? seen new-content-lower)
      ;; already seen
      {:state (assoc-in state [:append-dedupe path] seen)}
      ;; record and append
      {:state (assoc-in state [:append-dedupe path] (conj seen new-content))
       :write {path {:string (str "\n" new-content), :append true}}})))

(defn conflict-data-readers
  [{:keys [path in ^File existing]}]
  (let [existing-str (slurp existing)
        existing-reader-fns (edn/read-string existing-str)
        append-reader-fns (edn/read-string (stream->string in))
        reader-str (with-out-str (pprint/pprint (merge existing-reader-fns append-reader-fns)))]
    {:write {path {:string reader-str}}}))

(defn- conflict-warn
  [{:keys [path lib]}]
  (println "Conflicting path at" path "from" lib))

(defn- conflict-error
  [{:keys [path lib]}]
  (throw (ex-info (str "Conflicting path at " path " from " lib) {})))

(defn- handler-emit
  [entry buffer out-dir path write-spec]
  (let [{:keys [string stream append] :or {append false}} write-spec
        out-file (jio/file out-dir path)]
    (if string
      (spit out-file string :append ^boolean append)
      (copy-stream! ^InputStream stream (BufferedOutputStream. (FileOutputStream. out-file ^boolean append)) buffer))
    (Files/setLastModifiedTime (.toPath out-file) (.getLastModifiedTime ^JarEntry entry))))

(defn- handle-conflict
  [handlers entry buffer out-dir {:keys [state path] :as handler-params}]
  (let [use-handler (loop [[[re handler] & hs] (dissoc handlers :default)]
                      (if re
                        (if (re-matches re path)
                          handler
                          (recur hs))
                        (:default handlers)))]
    (if use-handler
      (let [{new-state :state, write :write} (use-handler handler-params)]
        (when write
          (doseq [[path write-spec] write]
            (handler-emit entry buffer out-dir path write-spec)))
        (or new-state state))
      (throw (ex-info (format "No handler found for conflict at %s" path) {})))))

(defn- ensure-dir
  "Returns true if parent dir exists, false if exists but is not a file,
  and throws if it cannot be created."
  [^File parent ^File child]
  (if (.exists parent)
    (.isDirectory parent)
    (if (jio/make-parents child)
      true
      (throw (ex-info (str "Unable to create parent dirs for: " (.toString child)) {})))))

(defn- explode
  [^File lib-file lib {:keys [out-dir buffer exclude handlers]} state]
  (cond
    (not (.exists lib-file))
    state

    (str/ends-with? (.getPath lib-file) ".jar")
    (with-open [jis (JarInputStream. (BufferedInputStream. (FileInputStream. lib-file)))]
      (loop [the-state state]
        (if-let [entry (.getNextJarEntry jis)]
          (let [path (.getName entry)
                out-file (jio/file out-dir path)
                parent-file (.getParentFile out-file)]
            (cond
              ;; excluded or directory - do nothing
              (or (exclude-from-uber? exclude path) (.isDirectory entry))
              (recur the-state)

              ;; conflict, same file from multiple sources - handle
              (.exists out-file)
              (recur (handle-conflict handlers entry buffer out-dir
                       {:lib lib, :path path, :in jis, :existing out-file, :state the-state}))

              ;; write new file, parent dir exists for writing
              (ensure-dir parent-file out-file)
              (do
                (copy-stream! ^InputStream jis (BufferedOutputStream. (FileOutputStream. out-file)) buffer)
                (Files/setLastModifiedTime (.toPath out-file) (.getLastModifiedTime entry))
                (recur the-state))

              :parent-dir-is-a-file
              (throw (ex-info (format "Cannot write %s from %s as parent dir is a file from another lib. One of them must be excluded."
                                path lib) {}))))
          the-state)))

    (.isDirectory lib-file)
    (do
      (file/copy-contents lib-file out-dir)
      state)

    :else
    (throw (ex-info (format "Unexpected lib file: %s" (.toString lib-file)) {}))))

(defn- remove-optional
  "Remove optional libs and their transitive dependencies from the lib tree.
  Only remove transitive if all dependents are optional."
  [libs]
  (let [by-opt (group-by (fn [[_lib coord]] (boolean (:optional coord))) libs)
        optional (apply conj {} (get by-opt true))]
    (if (seq optional)
      (loop [req (get by-opt false)
             opt optional]
        (let [under-opts (group-by (fn [[_lib {:keys [dependents]}]]
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
    (symbol? handler) (deref (requiring-resolve handler))
    (ifn? handler) handler
    :else (throw (ex-info (str "Invalid handler: " handler) {}))))

(def ^:private default-handlers
  {"^data_readers.clj[cs]?$" :data-readers
   "^META-INF/services/.*" :append
   "(?i)^(META-INF/)?(COPYRIGHT|NOTICE|LICENSE)(\\.(txt|md))?$" :append-dedupe
   :default :ignore})

(defn- prep-handlers
  "Transform user handler map into a map of re->fn"
  [handlers]
  (reduce-kv
    (fn [m pattern handler]
      (assoc m (if (= pattern :default) :default (re-pattern pattern))
               (prep-handler handler)))
    {} (merge default-handlers handlers)))

(defn uber
  [{mf-attrs :manifest, :keys [basis class-dir uber-file main exclude conflict-handlers]}]
  (let [working-dir (.toFile (Files/createTempDirectory "uber" (into-array FileAttribute [])))
        context {:out-dir working-dir
                 :buffer (byte-array 4096)
                 :handlers (prep-handlers conflict-handlers)
                 :exclude (map re-pattern (into uber-exclusions exclude))}]
    (try
      (let [{:keys [libs]} basis
            compile-dir (api/resolve-path class-dir)
            manifest (Manifest.)
            uber-file (api/resolve-path uber-file)
            mf-attr-strs (reduce-kv (fn [m k v] (assoc m (str k) (str v))) nil mf-attrs)]
        (reduce
          (fn [state [lib coord]]
            (reduce
              (fn [state path] (explode (jio/file path) lib context state))
              state (:paths coord)))
          nil ;; initial state, usable by handlers if needed
          (assoc (remove-optional libs) nil {:paths [compile-dir]}))
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