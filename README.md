tools.build
========================================

A library for building artifacts in Clojure projects.

# Usage as a library

```clojure
(require '[clojure.tools.build :as build] '[clojure.tools.build.tasks :as tasks])
(-> (build/build-info
      :deps "deps.edn"
      :params [{:build/lib 'foo/bar, :build/version "1.2.3"})]
    tasks/clean
    tasks/sync-pom
    tasks/jar)
```

# Usage as a deps tool

Likely to change...

Define the initial build parameters 

Add tools.build in as an alias in your ~/.clojure/deps.edn so it's available in any project:

```clojure
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
