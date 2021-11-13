tools.bbuild
========================================

## Babashka maintained fork of tools.build

> WARNING: this work is experimental and should be used with caution!

This fork of `tools.build` works in babashka. To make it compatible, the
following changes with the original tools.build were introduced:

- `compile-clj` changes to use clojure.core/munge instead of the
  Compiler internal.

- `install` changes to use a helper from tools-deps-native-experiment to
  construct maven objects.

- `javac` changes to use the javac command line.

## Usage

Ensure you have [babashka](https://github.com/babashka/babashka) 0.6.5 or later.

Download or build
[tools-deps-native](https://github.com/borkdude/tools-deps-native-experiment)
and put the binary on your path.

To use with babashka, add this to your `bb.edn`:

``` clojure
{:deps  {io.github.babashka/tools.bbuild
         {:git/sha "4803c45baf274143aeb185ad5b9843a23a5a08e7"}
         borkdude/spartan.spec
         {:git/url "https://github.com/borkdude/spartan.spec"
          :sha     "12947185b4f8b8ff8ee3bc0f19c98dbde54d4c90"}}
 :tasks {:requires    ([babashka.pods :as pods]
                       [spartan.spec]
                       [clojure.tools.build.api :as b])
         -tools.build {:task (do
                               (pods/load-pod "tools-deps-native")
                               (require '[clojure.tools.deps.alpha :as deps])
                               (require '[clojure.tools.build.api :as b]))}
         clean        {:depends [-tools.build]
                       :task    (b/delete {:path "target"})}
         write-pom    {:depends [-tools.build]
                       :task    (let [deps-file (clojure.java.io/file
                                                 "deps.edn")
                                      deps-edn  (deps/slurp-deps deps-file)
                                      basis     (deps/create-basis
                                                 {:project deps-edn})]
                                  (b/write-pom
                                   {:basis     basis
                                    :src-dirs  [ "src"]
                                    :class-dir "target/classes"
                                    :lib       'my/project
                                    :version   "1.0.0"}))}}}
```


Here follows the original README.

<hr>


A library for building artifacts in Clojure projects.

## Docs

* [API](https://clojure.github.io/tools.build)
* [Guide](https://clojure.org/guides/tools_build)

# Release Information

Latest release:

[deps.edn](https://clojure.org/reference/deps_and_cli) dependency information:

```
io.github.clojure/tools.build {:git/tag "v0.6.5" :git/sha "a0c3ff6"}
```

# Developer Information

* [GitHub project](https://github.com/clojure/tools.build)
* [How to contribute](https://clojure.org/community/contributing)
* [Bug Tracker](https://clojure.atlassian.net/browse/TBUILD)

# Copyright and License

Copyright © 2021 Rich Hickey, Alex Miller, and contributors

All rights reserved. The use and
distribution terms for this software are covered by the
[Eclipse Public License 1.0] which can be found in the file
epl-v10.html at the root of this distribution. By using this software
in any fashion, you are agreeing to be bound by the terms of this
license. You must not remove this notice, or any other, from this
software.

[Eclipse Public License 1.0]: http://opensource.org/licenses/eclipse-1.0.php
