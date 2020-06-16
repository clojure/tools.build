;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as jio]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.tools.build.tasks.pom :as pom]
    [clojure.tools.build :as build]
    [clojure.tools.build.file :as file]
    [clojure.tools.build.process :as process]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.maven :as mvn]
    [clojure.tools.namespace.find :as find]
    [clojure.set :as set])
  (:import
    [java.io File FileOutputStream FileInputStream BufferedInputStream BufferedOutputStream
             InputStream OutputStream ByteArrayOutputStream]
    [java.nio.file Path Files LinkOption FileSystems FileVisitor FileVisitResult]
    [java.nio.file.attribute BasicFileAttributes]
    [java.util.jar Manifest Attributes$Name JarOutputStream JarEntry JarInputStream JarFile]
    [java.util.zip ZipOutputStream ZipEntry]
    [javax.tools ToolProvider DiagnosticListener]
    [org.eclipse.aether.artifact DefaultArtifact]
    [org.eclipse.aether.installation InstallRequest InstallResult]))

(set! *warn-on-reflection* true)

;; dirs - sets up maven style default params

;; :flow/group-id
;; :flow/artifact-id
;; :flow/work-dir "target"
;; :flow/class-dir "target/classes"
;; :flow/jar-file "target/$ARTIFACT-[$CLASSIFIER-]$VERSION.jar"
;; :flow/pom-dir "target/classes/META-INF/$GROUP/$ARTIFACT
;; :flow/pom-file "target/classes/META-INF/$GROUP/$ARTIFACT/pom.xml
;; :flow/pom-properties-file "target/classes/META-INF/$GROUP/$ARTIFACT/pom.properties"
;; :flow/uber-file "target/classes/$ARTIFACT-[$CLASSIFIER]-$VERSION-standalone.jar"

(defn dirs
  [basis params]
  (let [lib (build/resolve-param basis params :build/lib)
        group-id (namespace lib)
        artifact-id (name lib)
        classifier (build/resolve-param basis params :build/classifier)
        version (build/resolve-param basis params :build/version)
        class-dir (jio/file "target" "classes")
        pom-dir (jio/file class-dir "META-INF" "maven" group-id artifact-id)
        jar-base (str artifact-id "-" version (if classifier (str "-" classifier) ""))
        jar-file (jio/file "target" (str jar-base ".jar"))
        uber-jar-file (jio/file "target" (str jar-base "-standalone.jar"))]
    {:build/group-id group-id
     :build/artifact-id artifact-id
     :build/target-dir "target"
     :build/class-dir (.getPath class-dir)
     :build/pom-dir (.getPath pom-dir)
     :build/jar-file (.getPath jar-file)
     :build/uber-file (.getPath uber-jar-file)}))

;; clean

(defn clean
  [basis {:build/keys [output-dir] :as params}]
  (let [target-dir (build/resolve-param basis params :build/target-dir)
        target-dir-file (jio/file output-dir target-dir)]
    (file/delete target-dir-file)))

;; compile-clj

(defn- write-compile-script
  ^File [^File target-dir ^File compile-dir nses compiler-opts]
  (let [script-file (jio/file target-dir (str (.getName compile-dir) ".clj"))
        script `(binding [~'*compile-path* ~(str compile-dir)
                          ~'*compiler-options* ~compiler-opts]
                  ~@(map (fn [n] `(~'compile '~n)) nses)
                  (System/exit 0))]
    (spit script-file (with-out-str (pprint/pprint script)))
    script-file))

(defn- ns->path
  [ns-sym]
  (str/replace (clojure.lang.Compiler/munge (str ns-sym)) \. \/))

(defn compile-clj
  [{:keys [classpath] :as basis} {:build/keys [project-dir output-dir] :as params}]
  (let [clj-paths (build/resolve-param basis params :build/clj-paths)
        target-dir (file/ensure-dir (jio/file output-dir (build/resolve-param basis params :build/target-dir)))
        class-dir (file/ensure-dir (jio/file output-dir (build/resolve-param basis params :build/class-dir)))
        compiler-opts (build/resolve-param basis params :build/compiler-opts)
        ns-compile (build/resolve-param basis params :build/ns-compile)
        filter-nses (build/resolve-param basis params :build/filter-nses)
        srcs (map #(build/maybe-resolve-param basis params %) clj-paths)
        nses (or ns-compile
               (mapcat #(find/find-namespaces-in-dir (jio/file project-dir %) find/clj) srcs))
        compile-dir (file/ensure-dir (jio/file target-dir "compile-clj"))
        compile-script (write-compile-script target-dir compile-dir nses compiler-opts)
        cp-str (-> classpath keys (conj compile-dir (.getPath class-dir)) deps/join-classpath)
        args ["java" "-cp" cp-str "clojure.main" (.getCanonicalPath compile-script)]
        exit (process/exec args)]
    (if (zero? exit)
      (if (seq filter-nses)
        (file/copy-contents compile-dir class-dir (map ns->path filter-nses))
        (file/copy-contents compile-dir class-dir))
      (throw (ex-info "Clojure compilation failed" {})))))

;; javac

(defn javac
  [{:keys [libs] :as basis} {:build/keys [project-dir output-dir] :as params}]
  (let [java-paths (build/resolve-param basis params :build/java-paths)]
    (when (seq java-paths)
      (let [javac-opts (build/resolve-param basis params :build/javac-opts)
            class-dir (file/ensure-dir (jio/file output-dir (build/resolve-param basis params :build/class-dir)))
            compiler (ToolProvider/getSystemJavaCompiler)
            listener (reify DiagnosticListener (report [_ diag] (println (str diag))))
            file-mgr (.getStandardFileManager compiler listener nil nil)
            class-dir-path (.getPath class-dir)
            classpath (str/join File/pathSeparator (conj (mapcat :paths (vals libs)) class-dir-path))
            options (concat ["-classpath" classpath "-d" class-dir-path] javac-opts)
            java-files (mapcat #(file/collect-files (jio/file project-dir %) :collect (file/suffixes ".java")) java-paths)
            file-objs (.getJavaFileObjectsFromFiles file-mgr java-files)
            task (.getTask compiler nil file-mgr listener options nil file-objs)
            success (.call task)]
        (when-not success
          (throw (ex-info "Java compilation failed" {})))))))

;; pom

(defn sync-pom
  [basis {:build/keys [project-dir output-dir] :as params}]
  (let [src-pom (or (build/resolve-param basis params :build/src-pom) "pom.xml")
        pom-dir (file/ensure-dir (jio/file output-dir (build/resolve-param basis params :build/pom-dir)))
        group-id (build/resolve-param basis params :build/group-id)
        artifact-id (build/resolve-param basis params :build/artifact-id)
        lib (build/resolve-param basis params :build/lib)
        version (build/resolve-param basis params :build/version)]
    (pom/sync-pom
      {:basis basis
       :params {:src-pom (.getPath (jio/file project-dir src-pom))
                :target-dir pom-dir
                :lib lib
                :version version}})
    (spit (jio/file pom-dir "pom.properties")
      (str/join (System/lineSeparator)
        ["# Generated by org.clojure/tools.build"
         (format "# %tc" (java.util.Date.))
         (format "version=%s" version)
         (format "groupId=%s" group-id)
         (format "artifactId=%s" artifact-id)]))))

;; copy

;; copy spec:
;;    :from (coll of dirs), default = ["."]
;;    ;include (glob), default = "**"
;;    :replace (map of replacements) - performed while copying

(defn- match-paths
  "Match glob to paths under root and return a collection of Path objects"
  [^File root glob]
  (let [root-path (.toPath root)
        matcher (.getPathMatcher (FileSystems/getDefault) (str "glob:" glob))
        paths (volatile! [])
        visitor (reify FileVisitor
                  (visitFile [_ path attrs]
                    (when (.matches matcher (.relativize root-path ^Path path))
                      (vswap! paths conj path))
                    FileVisitResult/CONTINUE)
                  (visitFileFailed [_ path ex] FileVisitResult/CONTINUE)
                  (preVisitDirectory [_ _ _] FileVisitResult/CONTINUE)
                  (postVisitDirectory [_ _ _] FileVisitResult/CONTINUE))]
    (Files/walkFileTree root-path visitor)
    @paths))

(def ^:private default-copy-spec
  {:from ["."] :include "**"})

(defn copy
  [basis {:build/keys [project-dir output-dir] :as params}]
  (let [copy-specs (build/resolve-param basis params :build/copy-specs)
        resolved-specs (map #(merge default-copy-spec %) copy-specs)
        to (build/resolve-param basis params :build/copy-to :build/class-dir)
        to-path (.toPath (file/ensure-dir (jio/file output-dir to)))]
    (doseq [{:keys [from include replace]} resolved-specs]
      ;(println "\nspec" from include to replace)
      (let [resolved-from (build/maybe-resolve-param basis params from)
            from (map #(build/maybe-resolve-param basis params %) (if (coll? resolved-from) resolved-from [resolved-from]))
            resolved-include (build/maybe-resolve-param basis params include)
            include (map #(build/maybe-resolve-param basis params %) (if (coll? resolved-include) resolved-include [resolved-include]))
            replace (reduce-kv #(assoc %1 %2 (build/maybe-resolve-param basis params %3)) {} replace)]
        (doseq [from-dir from]
          ;(println "from-dir" from-dir)
          (let [from-file (jio/file project-dir from-dir)]
            (doseq [include-one include]
              (let [paths (match-paths from-file include-one)]
                (doseq [^Path path paths]
                  (let [path-file (.toFile path)
                        target-file (.toFile (.resolve to-path (.relativize (.toPath from-file) path)))]
                    ;(println "copying" (.toString path-file) (.toString target-file) (boolean (not (empty? replace))))
                    (if (empty? replace)
                      (file/copy-file path-file target-file)
                      (let [contents (slurp path-file)
                            replaced (reduce (fn [s [find replace]] (str/replace s find replace))
                                       contents replace)]
                        (spit target-file replaced)))))))))))))

;; jar

(defn- add-zip-entry
  [^ZipOutputStream output-stream ^String path ^File file]
  (let [dir (.isDirectory file)
        attrs (Files/readAttributes (.toPath file) BasicFileAttributes ^"[Ljava.nio.file.LinkOption;" (into-array LinkOption []))
        path (if (and dir (not (.endsWith path "/"))) (str path "/") path)
        entry (doto (ZipEntry. path)
                ;(.setSize (.size attrs))
                ;(.setLastAccessTime (.lastAccessTime attrs))
                (.setLastModifiedTime (.lastModifiedTime attrs)))]
    (.putNextEntry output-stream entry)
    (when-not dir
      (with-open [fis (BufferedInputStream. (FileInputStream. file))]
        (jio/copy fis output-stream)))

    (.closeEntry output-stream)))

(defn- copy-to-zip
  ([^ZipOutputStream jos ^File root]
    (copy-to-zip jos root root))
  ([^ZipOutputStream jos ^File root ^File path]
   (let [root-path (.toPath root)
         files (file/collect-files root :dirs true)]
     (run! (fn [^File f]
             (let [rel-path (.toString (.relativize root-path (.toPath f)))]
               (when-not (= rel-path "")
                 ;(println "  Adding" rel-path)
                 (add-zip-entry jos rel-path f))))
       files))))

(defn- fill-manifest!
  [^Manifest manifest props]
  (let [attrs (.getMainAttributes manifest)]
    (run!
      (fn [[name value]]
        (.put attrs (Attributes$Name. ^String name) value)) props)))

(defn jar
  [basis {:build/keys [output-dir] :as params}]
  (let [main-class (build/resolve-param basis params :build/main-class)
        class-dir (build/resolve-param basis params :build/class-dir)
        jar-file (jio/file output-dir (build/resolve-param basis params :build/jar-file))
        class-dir-file (file/ensure-dir (jio/file output-dir class-dir))]
    (let [manifest (Manifest.)]
      (fill-manifest! manifest
        (cond->
          {"Manifest-Version" "1.0"
           "Created-By" "org.clojure/tools.build"
           "Build-Jdk-Spec" (System/getProperty "java.specification.version")}
          main-class (assoc "Main-Class" (str main-class))))
      (with-open [jos (JarOutputStream. (FileOutputStream. jar-file) manifest)]
        (copy-to-zip jos class-dir-file)))))

;; uberjar

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
                    (println "CONFLICT: " (.getName entry)))
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
  (let [class-dir (jio/file output-dir (build/resolve-param basis params :build/class-dir))
        lib (build/resolve-param basis params :build/lib)
        main-class (build/resolve-param basis params :build/main-class)
        version (build/resolve-param basis params :build/version)
        uber-dir (file/ensure-dir (jio/file output-dir target-dir "uber"))
        uber-file (jio/file output-dir (build/resolve-param basis params :build/uber-file))
        manifest (Manifest.)
        lib-paths (conj (->> libs remove-optional vals (mapcat :paths) (map #(jio/file %))) class-dir)]
    (run! #(explode % uber-dir) lib-paths)
    (fill-manifest! manifest
      (cond->
        {"Manifest-Version" "1.0"
         "Created-By" "org.clojure/tools.build"
         "Build-Jdk-Spec" (System/getProperty "java.specification.version")}
        main-class (assoc "Main-Class" (str main-class))
        (.exists (jio/file uber-dir "META-INF" "versions")) (assoc "Multi-Release" "true")))
    (with-open [jos (JarOutputStream. (FileOutputStream. uber-file) manifest)]
      (copy-to-zip jos uber-dir))))

;; zip

(defn zip
  [basis {:build/keys [output-dir] :as params}]
  (let [zip-dir (build/resolve-param basis params :build/zip-dir)
        zip-name (build/resolve-param basis params :build/zip-name)
        zip-dir-file (file/ensure-dir (jio/file output-dir zip-dir))
        zip-file (jio/file output-dir zip-name)
        zip-path (.toPath zip-dir-file)]
    (println "Zipping from" (.getPath zip-dir-file) "to" (.getPath zip-file))
    (with-open [zos (ZipOutputStream. (FileOutputStream. zip-file))]
      (copy-to-zip zos zip-dir-file))))

;; process

;; FUTURE: set directory, env vars, out/err handling
(defn process
  [basis {:build/keys [out>] :as params}]
  (let [command (build/resolve-param basis params :build/command)
        resolved-command (map #(build/maybe-resolve-param basis params %) command)
        out (process/invoke resolved-command)]
    (when out>
      {out> out})))

;; format-str

(defn format-str
  [basis {:build/keys [out>] :as params}]
  (let [template (build/resolve-param basis params :build/template)
        args (build/resolve-param basis params :build/args)
        resolved-args (map #(build/maybe-resolve-param basis params %) args)]
    {out> (apply format template resolved-args)}))

;; install

(defn install
  [{:mvn/keys [local-repo] :as basis} {:build/keys [output-dir] :as params}]
  (let [group-id (build/resolve-param basis params :build/group-id)
        artifact-id (build/resolve-param basis params :build/artifact-id)
        classifier (build/resolve-param basis params :build/classifier)
        version (build/resolve-param basis params :build/version)
        pom-dir (build/resolve-param basis params :build/pom-dir)
        jar-file (jio/file output-dir (build/resolve-param basis params :build/jar-file))
        pom (jio/file pom-dir "pom.xml")
        system (mvn/make-system)
        session (mvn/make-session system (or local-repo mvn/default-local-repo))
        jar-artifact (.setFile (DefaultArtifact. group-id artifact-id classifier "jar" version) jar-file)
        artifacts (cond-> [jar-artifact]
                    (and pom-dir (.exists pom)) (conj (.setFile (DefaultArtifact. group-id artifact-id classifier "pom" version) pom)))
        install-request (.setArtifacts (InstallRequest.) artifacts)]
    (.install system session install-request)
    nil))
