Changelog
===========

* v0.2.1 dd64636 on Sep 4, 2021
  * jar, uber - replace - with _ in main class name
* v0.2.0 7cbb94b on Aug 31, 2021
  * compile-clj - fix docs and code to not require :src-dirs
  * compile-clj - TBUILD-7 - sort namespaces using topological sort by default
* v0.1.9 6736c83 on Aug 22, 2021
  * git-count-revs - add :path option
  * pom-path - new task that computes the path to the pom.xml in a jar
* v0.1.8 38d2780 on Aug 13, 2021
  * write-file - TBUILD-15 - add :string option 
  * write-pom - TBUILD-13 - add :scm options to write scm properties
* v0.1.7 8a3abc2 on July 28, 2021
  * TBUILD-10 - fix missing assertions in tests
  * Remove unnecessary resource file that overrides tools.deps
* v0.1.6 5636e61 on July 21, 2021
  * copy-dir - Fix TBUILD-4 - set up default and overridable file ignore patterns
* v0.1.5 1cd59e6 on July 21, 2021
  * jar, uber - Fix TBUILD-8 - jar files built on Windows had bad paths
* v0.1.4 169fef9 on July 20, 2021
  * jar - add support for custom :manifest attributes
  * uber - add support for custom :manifest attributes
  * create-basis - make more tolerant of missing deps.edn file
  * update tools.deps.alpha dependency to latest
* v0.1.3 660a71f on July 13, 2021
  * write-pom - Fix TBUILD-3 - now takes deps from output of basis libs, so includes alias effects
  * uber - exclude META-INF/\*.MF files
* v0.1.2 81f05b7 on July 9, 2021
  * Update tools.deps.alpha dependency, public release
* v0.1.1 on July 7, 2021
  * Add `java-command` take to create Java command line from basis
* v0.1.0 on July 5, 2021
  * Renaming things towards release
