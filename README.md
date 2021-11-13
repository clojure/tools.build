tools.bbuild
========================================

## Babashka maintained fork of tools.build

> WARNING: this work is experimental and should be used with caution!

This fork of `tools.build` works with babashka. To make it compatible, the
following changes were introduced:

- The `clojure.tools.deps.alpha` library is replaced with
  [tools-deps-native](https://github.com/borkdude/tools-deps-native-experiment)
  which is used as a [pod](https://github.com/babashka/pods).

- `compile-clj` uses `clojure.core/munge` instead of `clojure.lang.Compiler/munge`

- `install` uses a helper from
  [tools-deps-native](https://github.com/borkdude/tools-deps-native-experiment)
  to construct maven objects.

- `javac` shells out to `javac` rather than using `javax.tools.JavaCompiler`

## Usage

Ensure you have [babashka](https://github.com/babashka/babashka) 0.6.5 or later.

Download or build
[tools-deps-native](https://github.com/borkdude/tools-deps-native-experiment)
and put the binary on your path.

Here is an example how to use this project in your `bb.edn`:

``` clojure
{:paths ["."]
 :deps  {io.github.babashka/tools.bbuild
         {:git/sha "7fb61e06e3caf91b35e625098682524237053b49"}
         borkdude/spartan.spec
         {:git/url "https://github.com/borkdude/spartan.spec"
          :sha     "12947185b4f8b8ff8ee3bc0f19c98dbde54d4c90"}}
 :tasks {:requires    ([build :as b])
         basis        {:task (b/basis {})}
         clean        {:task (b/clean {})}
         write-pom    {:depends [basis]
                       :task (b/write-pom {:basis basis})}
         jar          {:depends [basis write-pom]
                       :task (b/jar {:basis basis})}}}
```

with a `build.clj`:

``` clojure
(require '[babashka.pods :as pods])
;; Load tools-deps-native pod which defines clojure.tools.deps.alpha.
;; This assumes the binar tools-deps-native is on your PATH
;; You can change the call to load from an absolute or relative path instead.
(pods/load-pod "tools-deps-native")

(require '[spartan.spec]) ;; defines clojure.spec.alpha

(ns build
  (:require [clojure.tools.build.api :as b]))

(def version "0.1.0")
(def class-dir "target/classes")

(defn basis [_]
  (b/create-basis {:project "deps.edn"}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn write-pom [{:keys [basis]}]
  (b/write-pom
   {:basis     basis
    :src-dirs  ["src"]
    :class-dir class-dir
    :lib 'my/example
    :version version}))

(defn jar [{:keys [basis]}]
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar
   {:basis     basis
    :src-dirs  ["src"]
    :class-dir class-dir
    :main      "example.core"
    :jar-file  (format "target/example-%s.jar" version)}))
```

Then run e.g. `bb jar` to produce a jar file.

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
