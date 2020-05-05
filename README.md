tools.build
========================================

A library for building artifacts in Clojure projects.

# Usage as a library

```clojure
```

# Usage as a deps tool

Add to your deps.edn and add as a tool:

```clojure
{...
 {:aliases
  {:build
   {:deps {org.clojure/tools.build {:git/url "git@github.com:cognitect-labs/tools.build.git"
                                    :sha "5a0b814b6fc4e6d8fa03b9e64d93aef68f4f92a8"}}
    :main-opts ["build/make.clj"]}}}}
```

Example build script `(build/make.clj`):

```clojure
(require '[clojure.tools.build :as build])

(build/build
  '{:params
    {:build/target-dir "target"
     :build/class-dir "target/classes"
     :build/clj-paths ["src"]
     :build/copy-specs [{:from ["resources"]}]
     :build/src-pom "pom.xml"
     :build/lib your.org/lib
     :git-version/template "0.1.%s"
     :git-version/version> :flow/version
     :build/version :flow/version}
    :tasks [[clean]
            [clojure.tools.build.extra/git-version]
            [copy]
            [sync-pom]
            [jar]
            [install]]})

```

Run it in your current project:

```
clj -A:build
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
