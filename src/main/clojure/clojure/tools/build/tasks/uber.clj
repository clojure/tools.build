;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.uber
  (:require
    [clojure.java.io :as jio]
    [clojure.pprint :as pprint]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.util.file :as file]
    [clojure.tools.build.util.zip :as zip])
  (:import
    [java.io File InputStream IOException OutputStream ByteArrayOutputStream]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute FileTime]
    [java.util.jar JarEntry JarInputStream JarOutputStream Manifest]
    [java.util.concurrent Executors Future ForkJoinPool]
    [java.util.concurrent.atomic AtomicReference]))

(set! *warn-on-reflection* true)

;; Copy all the existing helper functions unchanged
(def ^:private uber-exclusions
  [#"project.clj"
   #"META-INF/.*\.(?:SF|RSA|DSA|MF)"
   #"module-info\.class"
   #"(.*/)?\.DS_Store" ;; Mac metadata
   #".+~" ;; emacs backup files
   #".#.*" ;; emacs
   #"(.*/)?\.keep" ;; convention in dirs to keep that are empty
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

;; Copy existing conflict handlers unchanged
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
      {:state (assoc-in state [:append-dedupe path] seen)}
      {:state (assoc-in state [:append-dedupe path] (conj seen new-content))
       :write {path {:string (str "\n" new-content), :append true}}})))

(defn conflict-data-readers
  [{:keys [path in ^File existing]}]
  (binding [*read-eval* false]
    (let [existing-str (slurp existing)
          existing-reader-fns (read-string
                                {:read-cond :preserve :features #{:clj}}
                                existing-str)
          append-reader-fns (read-string
                              {:read-cond :preserve :features #{:clj}}
                              (stream->string in))
          reader-str (with-out-str (pprint/pprint (merge existing-reader-fns append-reader-fns)))]
      {:write {path {:string reader-str}}})))

(defn- conflict-warn
  [{:keys [path lib]}]
  (println "Conflicting path at" path "from" lib))

(defn- conflict-error
  [{:keys [path lib]}]
  (throw (ex-info (str "Conflicting path at " path " from " lib) {})))

;; New parallel collection logic
(defrecord FileEntry [path content last-modified lib])

(defn- collect-from-jar
  "Collect all files from a JAR in parallel-friendly format"
  [^File jar-file lib exclude-patterns]
  (with-open [jis (JarInputStream. (jio/input-stream jar-file))]
    (loop [entries []]
      (if-let [entry (.getNextJarEntry jis)]
        (let [path (.getName entry)
              path (if (str/starts-with? path "/") (subs path 1) path)]
          (if (or (exclude-from-uber? exclude-patterns path) (.isDirectory entry))
            (recur entries)
            (let [content (let [baos (ByteArrayOutputStream.)]
                            (copy-stream! jis baos (byte-array 4096))
                            (.toByteArray baos))]
              (recur (conj entries (->FileEntry path content
                                                (.getLastModifiedTime ^JarEntry entry) lib))))))
        entries))))

(defn- collect-from-directory
  "Collect all files from a directory in parallel-friendly format"
  [^File dir-file lib exclude-patterns]
  (let [source-dir (.getAbsoluteFile dir-file)
        source-path (.toPath source-dir)
        files (file/collect-files source-dir :dirs false)]
    (keep
      (fn [^File f]
        (let [path (str/replace (.toString (.relativize source-path (.toPath f))) \\ \/)]
          (when-not (exclude-from-uber? exclude-patterns path)
            (try
              (let [content (with-open [is (jio/input-stream f)]
                              (let [baos (ByteArrayOutputStream.)]
                                (copy-stream! is baos (byte-array 4096))
                                (.toByteArray baos)))]
                (->FileEntry path content
                             (FileTime/fromMillis (.lastModified f)) lib))
              (catch IOException e
                (throw (ex-info (str "Can't read file in " lib " at " (.getPath f))
                                {:path (.getPath f)} e)))))))
      files)))

(defn- collect-files-from-lib
  "Collect all files from a single library (jar or directory)"
  [lib coord exclude-patterns]
  (mapcat
    (fn [path-str]
      (let [path-file (jio/file path-str)]
        (cond
          (not (.exists path-file))
          []

          (str/ends-with? (.getPath path-file) ".jar")
          (collect-from-jar path-file lib exclude-patterns)

          (.isDirectory path-file)
          (collect-from-directory path-file lib exclude-patterns)

          :else
          (throw (ex-info (format "Unexpected lib file: %s" (.toString path-file)) {})))))
    (:paths coord)))

(defn- group-files-by-conflict
  "Group files into conflicting and non-conflicting sets"
  [all-files]
  (let [files-by-path (group-by :path all-files)
        conflicting-paths (filter #(> (count (get files-by-path %)) 1)
                                  (keys files-by-path))
        non-conflicting-paths (filter #(= (count (get files-by-path %)) 1)
                                      (keys files-by-path))]
    {:conflicting (select-keys files-by-path conflicting-paths)
     :non-conflicting (into {}
                            (map (fn [path] [path (first (get files-by-path path))]))
                            non-conflicting-paths)}))

(defn- ensure-dir
  "Returns true if parent dir exists, false if exists but is not a file,
  and throws if it cannot be created."
  [^File parent ^File child]
  (if (.exists parent)
    (.isDirectory parent)
    (if (jio/make-parents child)
      true
      (throw (ex-info (str "Unable to create parent dirs for: " (.toString child)) {})))))

(defn- write-file-entry
  "Write a single file entry to disk"
  [^FileEntry entry out-dir]
  (let [out-file (jio/file out-dir (:path entry))]
    (when (ensure-dir (.getParentFile out-file) out-file)
      (with-open [os (jio/output-stream out-file)]
        (.write os ^bytes (:content entry)))
      (Files/setLastModifiedTime (.toPath out-file) (:last-modified entry)))))

(defn- write-non-conflicting-files
  "Write all non-conflicting files in parallel"
  [non-conflicting-files out-dir]
  (let [cpu-count (.. Runtime getRuntime availableProcessors)
        chunk-size (max 1 (quot (count non-conflicting-files) cpu-count))
        chunks (partition-all chunk-size (vals non-conflicting-files))]

    (if (< (count non-conflicting-files) 50)
      ;; Small number of files - write sequentially
      (doseq [file-entry (vals non-conflicting-files)]
        (write-file-entry file-entry out-dir))

      ;; Large number of files - write in parallel
      (let [futures (doall
                      (for [chunk chunks]
                        (future
                          (doseq [file-entry chunk]
                            (write-file-entry file-entry out-dir)))))]
        ;; Wait for all writes to complete
        (doseq [f futures]
          (deref f))))))

;; Copy existing conflict resolution infrastructure
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
  {"^data_readers.clj[c]?$" :data-readers
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

(defn- handler-emit
  [^FileTime last-modified-time buffer out-dir path write-spec]
  (let [{:keys [string stream append] :or {append false}} write-spec
        out-file (jio/file out-dir path)]
    (if string
      (spit out-file string :append ^boolean append)
      (copy-stream! ^InputStream stream (jio/output-stream out-file :append append) buffer))
    (Files/setLastModifiedTime (.toPath out-file) last-modified-time)))

(defn- handle-conflict
  [handlers last-modified-time buffer out-dir {:keys [state path] :as handler-params}]
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
            (handler-emit last-modified-time buffer out-dir path write-spec)))
        (or new-state state))
      (throw (ex-info (format "No handler found for conflict at %s" path) {})))))

(defn- resolve-conflicts
  "Resolve all conflicting files using the same logic as the original"
  [conflicting-files handlers buffer out-dir]
  (reduce
    (fn [state [path file-entries]]
      ;; Write first file, then handle conflicts with subsequent files
      (let [first-entry (first file-entries)
            first-file (jio/file out-dir path)]
        (write-file-entry first-entry out-dir)

        ;; Handle conflicts with remaining files
        (reduce
          (fn [current-state file-entry]
            (with-open [is (java.io.ByteArrayInputStream. (:content file-entry))]
              (handle-conflict handlers (:last-modified file-entry) buffer out-dir
                               {:lib (:lib file-entry)
                                :path path
                                :in is
                                :existing first-file
                                :state current-state})))
          state
          (rest file-entries))))
    nil
    conflicting-files))

;; Copy existing utility functions
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

;; Main parallel uber function
(defn uber
  [{mf-attrs :manifest, :keys [basis class-dir uber-file main exclude conflict-handlers]}]
  (let [working-dir (.toFile (Files/createTempDirectory "uber" (into-array FileAttribute [])))
        exclude-patterns (map re-pattern (into uber-exclusions exclude))
        handlers (prep-handlers conflict-handlers)
        buffer (byte-array 4096)]
    (try
      (let [{:keys [libs]} basis
            compile-dir (api/resolve-path class-dir)
            manifest (Manifest.)
            uber-file (api/resolve-path uber-file)
            mf-attr-strs (reduce-kv (fn [m k v] (assoc m (str k) (str v))) nil mf-attrs)

            ;; Prepare library entries
            lib-entries (assoc (remove-optional libs) nil {:paths [compile-dir]})

            ;; Phase 1: Collect all files in parallel
            _ (println "Phase 1: Collecting files from" (count lib-entries) "libraries...")
            start-time (System/currentTimeMillis)

            cpu-count (.. Runtime getRuntime availableProcessors)
            executor (Executors/newFixedThreadPool cpu-count)

            futures (doall
                      (for [[lib coord] lib-entries]
                        (.submit executor
                                 ^Callable
                                 (fn []
                                   (collect-files-from-lib lib coord exclude-patterns)))))

            all-files (mapcat #(.get ^Future %) futures)
            collect-time (- (System/currentTimeMillis) start-time)
            _ (println "Collected" (count all-files) "files in" collect-time "ms")

            ;; Phase 2: Group files by conflict status
            _ (println "Phase 2: Analyzing conflicts...")
            group-start (System/currentTimeMillis)

            {:keys [conflicting non-conflicting]} (group-files-by-conflict all-files)

            group-time (- (System/currentTimeMillis) group-start)
            _ (println "Found" (count conflicting) "conflicting paths,"
                       (count non-conflicting) "non-conflicting files in" group-time "ms")

            ;; Phase 3: Write non-conflicting files in parallel
            _ (println "Phase 3: Writing non-conflicting files...")
            write-start (System/currentTimeMillis)

            _ (write-non-conflicting-files non-conflicting working-dir)

            write-time (- (System/currentTimeMillis) write-start)
            _ (println "Wrote" (count non-conflicting) "files in" write-time "ms")

            ;; Phase 4: Resolve conflicts sequentially (maintaining deterministic behavior)
            _ (when (seq conflicting)
                (println "Phase 4: Resolving" (count conflicting) "conflicts...")
                (let [conflict-start (System/currentTimeMillis)]
                  (resolve-conflicts conflicting handlers buffer working-dir)
                  (println "Resolved conflicts in" (- (System/currentTimeMillis) conflict-start) "ms")))]

        (.shutdown executor)

        ;; Create JAR (unchanged from original)
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
        (with-open [jos (JarOutputStream. (jio/output-stream uber-file) manifest)]
          (zip/copy-to-zip jos working-dir))

        (println "Total uber time:" (- (System/currentTimeMillis) start-time) "ms"))
      (finally
        (file/delete working-dir)))))
