tools.build
========================================

A library for building artifacts in Clojure projects.

## Terms

* Basis - given a chain of deps.edn files and modifiers to resolve-deps, make-classpath, etc, the tools.deps library will produce a "runtime basis" consisting of the library and classpath execution environment and alias data
* Build - a build invocation loads a project basis and runs an ordered series of build tasks to create one or more artifacts
* Task - a build step, invoked with the basis, and a combination of shared parameters, task parameters, and flow parameters. Tasks may produce side effects (often writing to disk) and return new flow parameters that can be used by later tasks.
* Shared parameter - attribute provided to all tasks (ignored if not used by a task)
* Task parameter - attribute provided only to a single task, will oveerride any shared parameter
* Flow parameter - attribute produced as output by a task and available to subsequent tasks, must use the namespace `flow`
* Project directory - the directory containing deps.edn and all other project source files
* Target directory - the output directory, typically `<project-dir>/target`

## tools.build API

The primary function for executing a build is:
 `clojure.tools.build/build`

```clojure
(clojure.tools.build/build [{:keys [project-dir tasks params]}])

Executes a project build consisting of tasks using shared parameters.

  :project-dir (optional) - path to project root, should include deps.edn file (default = current directory),
                            used to form the project basis
  :output-dir (optional) - path to output root (default = current directory)
  :tasks (required) - coll of task steps in the form [task-sym task-params]
                      task-sym (required) - unqualified for built-in tasks, otherwise qualified
                      task-params (optional) - map of parameters overriding shared params
  :params (optional) - shared params passed to all tasks
```

All parameters should be qualified. Namespace of `build` and `flow` are reserved.

Tasks are functions that take the following form:

```clojure
(defn a-task [basis params])

  basis - the basis created by build params
  params - a merged map consisting of shared params, task params, and flow params
```

Tasks may return a map containing flow params (namespace must be `flow`) to be passed to subsequent tasks.

## Built-in tasks and parameters

The following built-in tasks are provided (these may be specified unqualified in `:tasks`):

| Task | Description |
| ---- | ----------- |
| `clean` | Clean target dir |
| `compile-clj` | Compile Clojure namespaces to classes |
| `sync-pom` | Use base pom.xml and deps.edn to produce an output pom.xml |
| `javac` | Compile Java source to classes |
| `copy` | Copy source and resource files to classes (w/string replacement) |
| `jar` | Create a jar containing classes etc |
| `uber` | Create an uberjar containing the jar and all dependent jars |
| `zip` | Zip output files |
| `process` | Execute an external process |
| `format-str` | Format a string template with param replacement |
| `install` | Install the created jar to local Maven cache |

### Shared directories and params

Many parameters are shared across multiple tasks - this is important both to reduce the number of parameters that need to be set and to allow tasks to work together. Important directories for the built-in tasks:

| Directory parameter | Description |
| ------------------- | ----------- |
| `:build/project-dir` | The project directory, which contains `deps.edn` and serves as the root for all input directories, defaults to current directory |
| `:build/output-dir` | The output directory, root for all output, defaults to current directory |
| `:build/target-dir` | The target directory, which contains all build output, either absolute or relative `:build/project-dir`. Usually "target". |
| `:build/class-dir` | The classes directory, which is the default output for compilation tasks and resources and the default directory to use when creating a jar file. Intepreted relative to `:build/target-dir`, typically `"classes"`. |
| `:build/clj-paths` | Coll of directories that are Clojure source roots, resolved relative to `:build/project-dir`. Used primarily by `clj-compile` task. |
| `:build/java-paths` | Coll of directories that are Java source roots, resolved relative to `:build/project-dir` |

Additionally, there are some project-oriented parameters that are shared across tasks:

| Shared parameter | Description |
| ---------------- | ----------- |
| `:build/lib` | Qualified symbol defining this projects lib name (in Maven terms `groupId/artifactId`) |
| `:build/classifier` | String specifying the library classifier |
| `:build/version` | String representing this project's version number |
| `:build/main-class` | Symbol for the Clojure namespace with a -main or Java main class |

If you are writing new tasks, you are encouraged to reuse these parameters (or possibly other task-specific parameters) where it makes sense.

### clean task

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/target-dir` | yes | Target dir |

Remove the target dir recursively.

### compile-clj task

| Basis key | Description |
| --------- | ----------- |
| `:classpath` | Classpath data from basis |

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/project-dir` | yes | Project dir |
| `:build/target-dir` | yes | Target dir |
| `:build/class-dir` | yes | Class output dir, resolved relative to `:build/target-dir` |
| `:build/clj-paths` | | Coll of Clojure source roots |
| `:build/compiler-opts` | | Map of [compiler options](https://clojure.org/reference/compilation#_compiler_options) |
| `:build/ns-compile` | | Coll of namespace symbols |
| `:build/filter-nses` | | Coll of namespace symbol roots |

The `compile-clj` task compiles either an explicit list of namespaces in `:build/ns-compile` or all namespaces detected in `:build/clj-paths`. Namespaces are compiled with `:build/compiler-opts` if provided and output to `:build/class-dir`. The `:build/filter-nses` is a coll of namespace symbols to keep after compilation.

Compilation occurs in a forked process using the `:classpath` from the computed project basis. Compilation errors will be printed to stderr and will cause build execution to abort.

Example compiling all Clojure namespaces in Clojure source paths (when making an uberjar for example):

```clojure
[compile-clj {:build/project-dir "..."
              :build/target-dir "target"
              :build/class-dir "classes"
              :build/clj-paths :clj-paths}]
```

Example compiling specific Clojure namespaces with direct linking and keeping only classes from this library:

```clojure
[compile-clj {:build/project-dir "..."
              :build/target-dir "target"
              :build/class-dir "classes"
              :build/ns-compile [a.b.c a.b.d]
              :build/compiler-options {:direct-linking true}
              :build/filter-nses [a.b]}]
```

### javac task

| Basis key | Description |
| --------- | ----------- |
| `:libs` | Lib map data from basis |

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/project-dir` | yes | Project dir |
| `:build/target-dir` | yes | Target dir |
| `:build/class-dir` | yes | Class output dir, resolved relative to `:build/target-dir` |
| `:build/java-paths` | yes | Coll of Java source roots, resolved relative to `:build/project-dir` |
| `:build/javac-opts` | | Coll of Java options to be used with javac |

Compile all Java source files under `:build/java-paths` with `:build/javac-opts` into `:build/class-dir`. Compilation occurs in-process. Compilation errors will be printed to stderr and will cause build execution to abort.

Example:

```clojure
[javac {:build/project-dir "..."
        :build/target-dir "target"
        :build/class-dir "classes"
        :build/java-paths :java-paths
        :build/javac-opts ["-source" "8" "-target" "8"]}]
```

### sync-pom task

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/project-dir` | yes | Project dir |
| `:build/target-dir` | yes | Target dir |
| `:build/class-dir` | yes | Class output dir, resolved relative to `:build/target-dir` |
| `:build/src-pom` | | Source pom file |
| `:build/lib` | yes | |
| `:build/version` | yes | |

Write pom.xml and pom.properties to `<class-dir>/META-INF/maven/group/artifact/`, matching Maven conventions. The `:build/src-pom` is used as a base pom.xml file if it exists, then updated with dependencies, repositories, src dir, maven coordinates, etc based on the params and/or the deps.edn in `:build/project-dir`.

Output flow params:

| Flow param | Description |
| ---------- | ----------- |
| `:flow/pom-file` | This task returns the location of the written pom file in this flow param |

### copy task

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/project-dir` | yes | Project dir |
| `:build/target-dir` | yes | Target dir |
| `:build/class-dir` | | Class output dir, resolved relative to `:build/target-dir` |
| `:build/copy-to` | | Directory, relative to `:build/target-dir` to copy to, defaults to `:build/class-dir` | 
| `:build/copy-specs` | yes | Coll of copy specs specifying what to copy |

Each copy spec has the following keys:

| Copy spec key | Description |
| ------------- | ----------- |
| `:from` | Directory or coll of dirs resolved relative to `:build/project-dir` |
| `:include` | File glob or coll of file globs to include |
| `:replace` | Map of string replacements to make in this copy, from source text to replacement text (which may also be params) |

The copy task copies all files specified by the copy specs to the `copy-to` directory (by default the classes dir), defaults intended for copying resource files (but other uses possible, typically with per-task overrides). The paths relative to `:from` are retained in the copy.

Copying Clojure sources for jar inclusion:

```clojure
[copy {:build/project-dir "..."
       :build/target-dir "target"
       :build/class-dir "classes"
       :build/copy-specs [{:from :clj-paths}]}]
```

Copying resources with replacement:

```clojure
[copy {:build/project-dir "..."
       :build/target-dir "target"
       :build/class-dir "classes"
       :build/copy-specs [{:from "resources" :replace {"$version" :build/version}}]}]
```

Copying licenses from legal dir:

```clojure
[copy {:build/project-dir "..."
       :build/target-dir "target"
       :build/class-dir "classes"
       :build/copy-specs [{:from "legal" :include "**license*"}]}]
```

### jar task

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/target-dir` | yes | Target dir |
| `:build/class-dir` | yes | Class output dir, resolved relative to `:build/target-dir` |
| `:build/lib` | yes | Qualified symbol defining this projects lib name (in Maven terms `groupId/artifactId`) |
| `:build/classifier` | | String specifying the library classifier |
| `:build/version` | yes | String representing this project's version number |
| `:build/main-class` | | Symbol for the Clojure namespace with a -main or Java main class |

Create jar file named `<artifact>-<version>.jar` in `:build/target-dir` containing contents of `:build/class-dir`. Manifest will have `:build/main-class` set.

### uber task

| Basis key | Description |
| --------- | ----------- |
| `:libs` | Lib map data from basis |

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/target-dir` | yes | Target dir |
| `:build/class-dir` | yes | Class output dir, resolved relative to `:build/target-dir` |
| `:build/lib` | yes | Qualified symbol defining this projects lib name (in Maven terms `groupId/artifactId`) |
| `:build/version` | yes | String representing this project's version number |
| `:build/main-class` | | Symbol for the Clojure namespace with a -main or Java main class |

Create an uber jar that contains the contents of the `:build/class-dir` and all library dependencies from the basis lib map. Set main-class in the manifest.

These resources are filtered (not currently configurable):

* `#"META-INF/.*\.(?:SF|RSA|DSA)"`

In the case of multiple jars with the same resource (not currently configurable):

* data_readers.clj(c) - merge
* anything else - print conflict to stdout

### zip task

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/target-dir` | yes | Target dir |
| `:build/zip-dir` | yes | Directory relative to `:build/target-dir` to assemble zip |
| `:build/zip-name` | yes | Name of output zip file, relative to target-dir |

Creates zip file of zip-dir's contents in zip-name.

### process task

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/command` | yes | Coll of process params |
| `:build/out>` |  | Flow param key with which to return the process output |

Expect the command as specified in command and return the trimmed stdout result in the specified flow param.

Output flow params:

| Flow param | Description |
| ---------- | ----------- |
| Value of `:build/out>` | Return the trimmed stdout result of executing the command |

### format-str task

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/template` | yes | String template per Java formatter |
| `:build/args` | yes | Coll of args (resolved as params) to feed the template |
| `:build/out>` | yes | Flow param key with which to return the process output |

Format the string template with the args and put the result in the out> flow param.

Output flow params:

| Flow param | Description |
| ---------- | ----------- |
| Value of `:build/out>` | Return the formatting template |

### install task

* Prereq tasks: expects jar file from `jar` task and pom file from `sync-pom` task

| Basis key | Description |
| --------- | ----------- |
| `:mvn/local-repo` | Local repository location (default to ~/.m2/repository) |

| Parameter | Required? | Description |
| --------- | --------- | ----------- |
| `:build/target-dir` | yes | Target dir |
| `:build/lib` | yes | Qualified symbol defining this projects lib name (in Maven terms `groupId/artifactId`) |
| `:build/classifier` | | String specifying the library classifier |
| `:build/version` | yes | String representing this project's version number |
| `:flow/pom-file` | yes | Location of pom file created by `sync-pom` | 

Installs the jar (created by the `jar` task) into the Maven local repository.

## Usage as a deps tool

Add to your deps.edn and add as a tool:

```clojure
{...
 :aliases
 {:build
  {:deps {org.clojure/tools.build {:git/url "git@github.com:cognitect-labs/tools.build.git"
                                   :sha "<SHA>"}
          org.clojure/tools.deps.alpha {:git/url "https://github.com/clojure/tools.deps.alpha.git"
                                        :sha "<SHA>"}
          org.slf4j/slf4j-nop {:mvn/version "1.7.25"}}
   :run-fn clojure.tools.build/build
   :run-args {:tasks [[clean] [copy] [sync-pom] [jar]]
              :params {:build/target-dir "target"
                       :build/class-dir "classes"
                       :build/copy-specs [{:from :clj-paths}]
                       :build/src-pom "pom.xml"
                       :build/lib my/lib1
                       :build/version "1.2.3"}}}}}
```

You can find the latest shas for these projects with:

    git ls-remote git@github.com:cognitect-labs/tools.build.git refs/heads/master
    git ls-remote https://github.com/clojure/tools.deps.alpha.git refs/heads/calc-basis


Run it: 

```
clj -A:build -X:build
```

Override a parameter like version:

```
clj -A:build -X:build :params:build/version "\"2.2.2\""
```

# Release Information

Latest release: not yet released

* [All released versions](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.clojure%22%20AND%20a%3A%22tools.build%22)

[deps.edn](https://clojure.org/guides/deps_and_cli) dependency information:

```
org.clojure/tools.build {:mvn/version "TBD"}
```

# Developer Information

* [GitHub project](https://github.com/clojure/tools.build)
* [How to contribute](https://clojure.org/community/contributing)
* [Bug Tracker](https://dev.clojure.org/jira/browse/TDEPS)
* [Continuous Integration](https://build.clojure.org/job/tools.build/)
* [Compatibility Test Matrix](https://build.clojure.org/job/tools.build-test-matrix/)

# Copyright and License

Copyright © 2020 Rich Hickey, Alex Miller, and contributors

All rights reserved. The use and
distribution terms for this software are covered by the
[Eclipse Public License 1.0] which can be found in the file
epl-v10.html at the root of this distribution. By using this software
in any fashion, you are agreeing to be bound by the terms of this
license. You must not remove this notice, or any other, from this
software.

[Eclipse Public License 1.0]: http://opensource.org/licenses/eclipse-1.0.php
