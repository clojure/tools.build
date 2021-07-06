(ns clojure.tools.build.api
  (:require
    [clojure.java.io :as jio]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.build.util.file :as file])
  (:import
    [java.io File]))

(set! *warn-on-reflection* true)

;; Project root dir, defaults to current directory

(def ^:dynamic *project-root*
  "Project root path, defaults to current directory.
  Use `resolve-path` to resolve relative paths in terms of the *project-root*.
  Use `set-project-root!` to override the default for all tasks."
  ".")

(defn set-project-root!
  "Set *project-root* dir (default is \".\")"
  [root]
  (alter-var-root #'*project-root* (constantly root)))

(defn resolve-path
  "If path is absolute or root-path is nil then return path,
  otherwise resolve relative to *project-root*."
  ^File [path]
  (let [path-file (jio/file path)]
    (if (.isAbsolute path-file)
      ;; absolute, ignore root
      path-file
      ;; relative to *project-root*
      (jio/file *project-root* path-file))))

(defn- assert-required
  "Check that each key in required coll is a key in params and throw if
  required are missing in params, otherwise return nil."
  [task params required]
  (let [missing (set/difference (set required) (set (keys params)))]
    (when (seq missing)
      (throw (ex-info (format "Missing required params for %s: %s" task (vec (sort missing))) (or params {}))))))

;; File tasks

(defn delete
  "Delete file or directory recursively, if it exists. Returns nil.

  Options:
    :path - required, path to file or directory"
  [{:keys [path] :as params}]
  (assert-required "delete" params [:path])
  (let [root-file (resolve-path path)]
    ;(println "root-file" root-file)
    (if (.exists root-file)
      (file/delete root-file))))

(defn copy-file
  "Copy one file from source to target, creating target dirs if needed.
  Returns nil.

  Options:
    :src - required, source path
    :target - required, target path"
  [{:keys [src target] :as params}]
  (assert-required "copy-file" params [:src :target])
  (file/copy-file (resolve-path src) (resolve-path target)))

(defn write-file
  "Like spit, but create dirs if needed. Returns nil.

  Options:
    :path - required, file path
    :content - val to write, will pr-str (if omitted, like touch)
    :opts - coll of writer opts like :append and :encoding (per clojure.java.io)"
  [{:keys [path content opts] :as params}]
  (assert-required "write-file" params [:path])
  (let [f (resolve-path path)]
    (if content
      (apply file/ensure-file f (pr-str content) opts)
      (file/ensure-file f))))

(defn copy-dir
  "Copy the contents of the src-dirs to the target-dir, optionally do text replacement.
  Returns nil.

  Globs are wildcard patterns for specifying sets of files in a directory
  tree, as specified in the glob syntax of java.nio.file.FileSystem:
  https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)

  Options:
    :target-dir - required, dir to write files, will be created if it doesn't exist
    :src-dirs   - required, coll of dirs to copy from
    :include    - glob of files to include, default = \"**\"
    :replace    - map of source to replacement string in files"
  [params]
  (assert-required "copy" params [:target-dir :src-dirs])
  ((requiring-resolve 'clojure.tools.build.tasks.copy/copy) params))

;; Basis tasks

(defn create-basis
  "Create a basis from a set of deps sources and a set of aliases. By default, use
  root, user, and project deps and no aliases (essentially the same classpath you
  get by default from the Clojure CLI).

  Each dep source value can be :standard, a string path, a deps edn map, or nil.
  Sources are merged in the order - :root, :user, :project, :extra.

  Aliases refer to argmaps in the merged deps that will be supplied to the basis
  subprocesses (tool, resolve-deps, make-classpath-map).

  The following subprocess argmap args can be provided:
    Key                  Subproc             Description
    :replace-deps        tool                Replace project deps
    :replace-paths       tool                Replace project paths
    :extra-deps          resolve-deps        Add additional deps
    :override-deps       resolve-deps        Override coord of dep
    :default-deps        resolve-deps        Provide coord if missing
    :extra-paths         make-classpath-map  Add additional paths
    :classpath-overrides make-classpath-map  Replace lib path in cp

  Options (note, paths resolved via *project-root*):
    :root    - dep source, default = :standard
    :user    - dep source, default = :standard
    :project - dep source, default = :standard (\"./deps.edn\")
    :extra   - dep source, default = nil
    :aliases - coll of aliases of argmaps  to apply to subprocesses

  Returns a runtime basis, which is the initial merged deps edn map plus these keys:
   :resolve-args - the resolve args passed in, if any
   :classpath-args - the classpath args passed in, if any
   :libs - lib map, per resolve-deps
   :classpath - classpath map per make-classpath-map
   :classpath-roots - vector of paths in classpath order"
  ([]
   ((requiring-resolve 'clojure.tools.build.tasks.create-basis/create-basis)))
  ([params]
   ((requiring-resolve 'clojure.tools.build.tasks.create-basis/create-basis) params)))

;; Process tasks

(defn process
  "Exec the command made from command-args, redirect out and err as directed,
  and return {:exit exit-code, :out captured-out, :err captured-err}

  Options:
    :command-args - required, coll of string args
    :dir - directory to run the command from, default current directory
    :out - one of :inherit :capture :write :append :ignore
    :err - one of :inherit :capture :write :append :ignore
    :out-file - file path to write if :out is :write or :append
    :err-file - file path to write if :err is :write or :append
    :env - map of environment variables to set

  The :out and :err input flags take one of the following options:
    :inherit - inherit the stream and write the subprocess io to this process's stream (default)
    :capture - capture the stream to a string and return it
    :write - write to :out-file or :err-file
    :append - append to :out-file or :err-file
    :ignore - ignore the stream"
  [params]
  (assert-required "process" params [:command-args])
  ((requiring-resolve 'clojure.tools.build.tasks.process/process) params))

;; Git tasks

(defn git-count-revs
  "Shells out to git and returns count of commits on this branch:
    git rev-list HEAD --count

  Options:
    :dir - dir to invoke this command from, by default current directory"
  [{:keys [dir] :or {dir "."} :as params}]
  (-> {:command-args ["git" "rev-list" "HEAD" "--count"]
       :dir (.getPath (resolve-path dir))
       :out :capture}
    process
    :out
    str/trim))

;; Compile tasks

(defn compile-clj
  "Compile Clojure source to classes. Returns nil.

  Options:
    :basis - required, basis to use when compiling
    :src-dirs - required, coll of Clojure source dirs
    :class-dir - required, dir to write classes, will be created if needed
    :compile-opts - map of Clojure compiler options:
      {:disable-locals-clearing false
       :elide-meta [:doc :file :line ...]
       :direct-linking false}
    :ns-compile - coll of namespace symbols to compile, all if not specified
    :filter-nses - coll of symbols representing a namespace prefix to include"
  [params]
  (assert-required "compile-clj" params [:basis :src-dirs :class-dir])
  ((requiring-resolve 'clojure.tools.build.tasks.compile-clj/compile-clj) params))

(defn javac
  "Compile Java source to classes. Returns nil.

  Options:
    :src-dirs - required, coll of Java source dirs
    :class-dir - required, dir to write classes, will be created if needed
    :basis - classpath basis to use when compiling
    :javac-opts - coll of string opts, like [\"-source\" \"8\" \"-target\" \"8\"]"
  [params]
  (assert-required "javac" params [:src-dirs :class-dir])
  ((requiring-resolve 'clojure.tools.build.tasks.javac/javac) params))

;; Jar/zip tasks

(defn write-pom
  "Write pom.xml and pom.properties files to the class dir under
  META-INF/maven/group-id/artifact-id/ (where Maven typically writes
  these files). The pom deps, dirs, and repos are either synced from
  the src-pom or generated from the basis.

  If a repos map is provided it supersedes the repos in the basis.

  Returns nil.

  Options:
    :basis - required, used to pull deps, repos
    :class-dir - required, root dir for writing pom files, created if needed
    :src-pom - source pom.xml to synchronize from, default = \"./pom.xml\"
    :lib - required, project lib symbol
    :version - required, project version
    :src-dirs - coll of src dirs
    :resource-dirs - coll of resource dirs
    :repos - map of repo name to repo config, replaces repos from deps.edn"
  [params]
  (assert-required "write-pom" params [:basis :class-dir :lib :version])
  ((requiring-resolve 'clojure.tools.build.tasks.sync-pom/sync-pom) params))

(defn jar
  "Create jar file containing contents of class-dir. Use main in the manifest
  if provided. Returns nil.

  Options:
    :class-dir - required, dir to include in jar
    :jar-file - required, jar to write
    :main - main class symbol"
  [params]
  (assert-required "jar" params [:class-dir :jar-file])
  ((requiring-resolve 'clojure.tools.build.tasks.jar/jar) params))

(defn uber
  "Create uberjar file containing contents of deps in basis and class-dir.
  Use main class in manifest if provided. Returns nil.

  Options:
    :class-dir - required, local class dir to include
    :uber-file - required, uber jar file to create
    :basis - used to pull dep jars
    :main - main class symbol"
  [params]
  (assert-required "uber" params [:class-dir :uber-file])
  ((requiring-resolve 'clojure.tools.build.tasks.uber/uber) params))

(defn zip
  "Create zip file containing contents of src dirs. Returns nil.

  Options:
    :src-dirs - required, coll of source directories to include in zip
    :zip-file - required, zip file to create"
  [params]
  (assert-required "zip" params [:src-dirs :zip-file])
  ((requiring-resolve 'clojure.tools.build.tasks.zip/zip) params))

;; Maven tasks

(defn install
  "Generate pom file and install pom and jar to local Maven repo.
  Returns nil.

  Options:
    :basis - required, used for :mvn/local-repo
    :lib - required, lib symbol
    :classifier - classifier string, if needed
    :version - required, string version
    :jar-file - required, path to jar file
    :class-dir - required, used to find the pom file"
  [params]
  (assert-required "install" params [:basis :lib :version :jar-file :class-dir])
  ((requiring-resolve 'clojure.tools.build.tasks.install/install) params))

