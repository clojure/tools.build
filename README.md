tools.bbuild
========================================

## Babashka maintained fork of tools.build

> WARNING: this work is experimental and should be used with caution!

This fork of `tools.build` works with babashka. To make it compatible, the
following changes were introduced:

- The `clojure.tools.deps` library is replaced with
  [tools-deps-native](https://github.com/borkdude/tools-deps-native-experiment)
  which is used as a [pod](https://github.com/babashka/pods).

- `compile-clj` uses `clojure.core/munge` instead of `clojure.lang.Compiler/munge`

- `install` uses a helper from
  [tools-deps-native](https://github.com/borkdude/tools-deps-native-experiment)
  to construct maven objects.

- `javac` shells out to `javac` rather than using `javax.tools.JavaCompiler`

## Usage

Ensure you have [babashka](https://github.com/babashka/babashka) 1.0.169 or later.

In your `bb.edn` add `:paths ["."]` to add `build.clj` to your babashka classpath.
Also add this library to `bb.edn`:

``` clojure
io.github.babashka/tools.bbuild {:git/sha "<sha>"}
```

Create a `build.clj`:

``` clojure
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

Then run e.g. `bb -x build/jar` to produce a jar file.

## Tests

To run tests, run `bb test`. This assumes that the `tools-deps-native` pod is on
your PATH.

Here follows the original README.

<hr>


A library for building artifacts in Clojure projects.

## Docs

* [API](https://clojure.github.io/tools.build)
* [Guide](https://clojure.org/guides/tools_build)

# Release Information

Latest release:

[deps.edn](https://clojure.org/reference/deps_and_cli) dependency information:

As a git dep:

```clojure
io.github.clojure/tools.build {:git/tag "v0.9.5" :git/sha "24f2894"}
```

As a Maven dep:

```clojure
io.github.clojure/tools.build {:mvn/version "0.9.5"}
```

# Developer Information

[![Tests](https://github.com/clojure/tools.build/actions/workflows/ci.yml/badge.svg)](https://github.com/clojure/tools.build/actions/workflows/ci.yml)

* [GitHub project](https://github.com/clojure/tools.build)
* [How to contribute](https://clojure.org/community/contributing)
* [Bug Tracker](https://clojure.atlassian.net/browse/TBUILD)

# Copyright and License

Copyright © 2023 Rich Hickey, Alex Miller, and contributors

All rights reserved. The use and
distribution terms for this software are covered by the
[Eclipse Public License 1.0] which can be found in the file
epl-v10.html at the root of this distribution. By using this software
in any fashion, you are agreeing to be bound by the terms of this
license. You must not remove this notice, or any other, from this
software.

[Eclipse Public License 1.0]: http://opensource.org/licenses/eclipse-1.0.php
