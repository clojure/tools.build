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
  "Set project root dir (default is \".\")"
  [root]
  (alter-var-root #'*project-root* (constantly root)))

(defn resolve-path
  "If path is absolute or root-path is nil then return path,
  otherwise resolve path under root-path."
  ^File [path]
  (let [path-file (jio/file path)]
    (if (.isAbsolute path-file)
      ;; absolute, ignore root
      path-file
      ;; relative to *project-root*
      (jio/file *project-root* path-file))))

(defn- assert-required
  "Check that each key in required coll is a key in params, throw if not."
  [task params required]
  (let [missing (set/difference (set required) (set (keys params)))]
    (when (seq missing)
      (throw (ex-info (format "Missing required params for %s: %s" task (vec (sort missing))) (or params {}))))))

;; File tasks

(defn delete
  "Delete file or directory recursively, if it exists.

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

  Options:
    :src - required, source path
    :target - required, target path"
  [{:keys [src target] :as params}]
  (assert-required "copy-file" params [:src :target])
  (file/copy-file (resolve-path src) (resolve-path target)))

(defn write-file
  "Like spit, but create dirs if needed.

  Options:
    :path - required, file path
    :content - val to write, will pr-str (if omitted, like touch)
    :opts - coll of writer opts like :append and :encoding"
  [{:keys [path content opts] :as params}]
  (assert-required "write-file" params [:path])
  (let [f (resolve-path path)]
    (if content
      (apply file/ensure-file f (pr-str content) opts)
      (file/ensure-file f))))

(defn copy
  "Copy many files and optionally do text replacement.

  Options:
    :target-dir - required, dir to write files, will be created if it doesn't exist
    :src-dirs   - required, coll of dirs to copy from
    :include    - glob of files to include, default = \"**\"
    :replace    - map of source to replacement string in files"
  [params]
  (assert-required "copy" params [:target-dir :src-dirs])
  ((requiring-resolve 'clojure.tools.build.tasks.copy/copy) params))

;; Basis tasks

(defn load-basis
  "Load the project basis (classpath context based on project deps.edn)
  and returns it.

  Options:
    :deps-file - path to deps file, default = deps.edn"
  ([]
   ((requiring-resolve 'clojure.tools.build.tasks.load-basis/load-basis)))
  ([params]
   ((requiring-resolve 'clojure.tools.build.tasks.load-basis/load-basis) params)))

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
  "Shells out to git to count the number of commits on this branch:
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
  "Compile Clojure source to classes.

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
  "Compile Java source to classes.

  Options:
    :src-dirs - required, coll of Java source dirs
    :class-dir - required, dir to write classes, will be created if needed
    :basis - classpath basis to use when compiling
    :javac-opts - coll of string opts, like [\"-source\" \"8\" \"-target\" \"8\"]"
  [params]
  (assert-required "javac" params [:src-dirs :class-dir])
  ((requiring-resolve 'clojure.tools.build.tasks.javac/javac) params))

;; Jar/zip tasks

(defn sync-pom
  "Sync or generate pom from deps.edn.

  Options:
    :basis - required, used to pull deps, repos
    :class-dir - required, dir to write classes, will be created if needed
    :src-pom - source pom.xml to synchronize from
    :lib - required, project lib symbol
    :version - required, project version
    :src-dirs - coll of src dirs
    :resource-dirs - coll of resource dirs
    :repos - map of repo name to repo config, replaces repos from deps.edn"
  [params]
  (assert-required "sync-pom" params [:basis :class-dir :lib :version])
  ((requiring-resolve 'clojure.tools.build.tasks.sync-pom/sync-pom) params))

(defn jar
  "Create jar file.

  Options:
    :class-dir - required, dir to include in jar
    :jar-file - required, jar to write
    :main - main class symbol"
  [params]
  (assert-required "jar" params [:class-dir :jar-file])
  ((requiring-resolve 'clojure.tools.build.tasks.jar/jar) params))

(defn uber
  "Create uberjar file.

  Options:
    :class-dir - required, local class dir to include
    :uber-file - required, uber jar file to create
    :basis - used to pull dep jars
    :main - main class symbol"
  [params]
  (assert-required "uber" params [:class-dir :uber-file])
  ((requiring-resolve 'clojure.tools.build.tasks.uber/uber) params))

(defn zip
  "Create zip file.

  Options:
    :src-dirs - required, coll of source directories to include in zip
    :zip-file - required, zip file to create"
  [params]
  (assert-required "zip" params [:src-dirs :zip-file])
  ((requiring-resolve 'clojure.tools.build.tasks.zip/zip) params))

;; Maven tasks

(defn install
  "Install Maven jar to local repo.

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

