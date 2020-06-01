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
 :aliases
 {:build
  {:deps {org.clojure/tools.build {:git/url "git ls-remote https://github.com/clojure/tools.build.git refs/heads/master"
                                   :sha "<SHA>"}
          org.clojure/tools.deps.alpha {:git/url "https://github.com/clojure/tools.deps.alpha.git"
                                        :sha "<SHA>"}}
          org.slf4j/slf4j-nop {:mvn/version "1.7.25"}
   :run-fn clojure.tools.build/build
   :run-args {:tasks [[clean] [copy] [sync-pom] [jar]]
              :params {:build/target-dir "target"
                       :build/class-dir "classes"
                       :build/copy-specs [{:from :clj-paths}]
                       :build/src-pom "pom.xml"
                       :build/lib my/lib1
                       :build/version "1.2.3"}}}
  }}
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
