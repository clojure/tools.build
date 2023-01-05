Changelog
===========

* v0.9.0 8c93e0c on Dec 22, 2022
  * Add clojure.tools.build.api/with-project-root macro
  * java-command, compile-clj - TBUILD-34 - Use Clojure CLI logic in finding Java executable
  * Switch to tools.deps 0.16.1264
* v0.8.5 9c738da on Nov 14, 2022
  * Add support for snapshot and release policies on :mvn/repos (see TDEPS-101)
* v0.8.4 8c3cd69 on Nov 3, 2022
  * TBUILD-26 Released as a Maven artifact
* v0.8.3 0d20256 on Jun 28, 2022
  * uber - TBUILD-32 - Preserve reader conditionals when merging data\_readers.cljc
  * uber - TBUILD-33 - Adjust regex for data\_readers to omit .cljs
  * Update to tools.deps.alpha 0.14.1212
* v0.8.2 ba1a2bf on May 6, 2022
  * Update deps to latest
* v0.8.1 7d40500 on Mar 11, 2022
  * compile-clj - TBUILD-29 - add support for setting bindings during compilation 
* v0.8.0 e3e3532 on Feb 24, 2022
  * compile-clj - always create classpath entries relative to `*project-root*`
  * java-command - don't resolve classpath entries, leave them relative to `*project-root*`
* v0.7.7 1474ad6 on Feb 18, 2022
  * compile-clj - TBUILD-27 - Fix bug in prior impl
* v0.7.6 3549b5f on Feb 18, 2022
  * compile-clj - TBUILD-27 - Use basis as default src dirs
  * Update to tools.deps.alpha 0.12.1148
* v0.7.5 34727f7 on Jan 5, 2022
  * Update to tools.deps.alpha 0.12.1109
* v0.7.4 ac442da on Dec 23, 2021
  * Update to tools.deps.alpha 0.12.1104
* v0.7.3 2699924 on Dec 22, 2021
  * Update to tools.deps.alpha 0.12.1098 (fix occasional race condition in parallel load of S3TransporterFactory)
* v0.7.2 0361dde on Dec 13, 2021
  * copy-dir - copy posix file permissions only when posix permissions are supported
* v0.7.1 13f0fec on Dec 12, 2021
  * copy-dir - TBUILD-24 - retain file permissions when doing string replace
* v0.7.0 16eddbf on Dec 12, 2021
  * write-pom - TBUILD-23 - specify explicit output path with :target
  * Update to tools.namespace 1.2.0
  * Update to tools.deps.alpha 0.12.1090
* v0.6.8 d79ae84 on Nov 26, 2021
  * uber - fix service append regex
* v0.6.7 8cca4f4 on Nov 24, 2021
  * git-process - remove debug output
* v0.6.6 4d41c26 on Nov 14, 2021
  * install - fix use of deprecated local-repo default in install - thanks @borkdude!
* v0.6.5 a0c3ff6 on Nov 12, 2021
  * git-process - NEW task to run an arbitrary git process and return the output
  * git-rev-count - updated to use git-process, added :git-command attribute
* v0.6.4 ea76dff on Nov 12, 2021
  * java-command - add control over using classpath file with :use-cp-file (default=:auto)
  * compile-clj - can now accept java-command passthrough args :java-cmd, :java-opts, :use-cp-file
* v0.6.3 4a1b53a on Nov 8, 2021
  * Update to tools.deps 0.12.1071
* v0.6.2 226fb52 on Oct 12, 2021
  * Update to tools.deps 0.12.1053
* v0.6.1 515b334 on Oct 10, 2021
  * copy-dir - update attribute name added in v0.6.0
* v0.6.0 b139316 on Oct 10, 2021
  * compile-clj - TBUILD-20 - fix regression with including class dir on classpath
  * copy-dir - add option to copy but not replace in binary files by extension
* v0.5.1 21da7d4 on Sep 21, 2021
  * Update to latest tools.deps 0.12.1048
* v0.5.0 7d77952 on Sep 16, 2021
  * create-basis - do not include user deps.edn by default
* v0.4.1 452db44 on Sep 16, 2021
  * Add some param spec checking
* v0.4.0 801a22f on Sep 15, 2021
  * uber - TBUILD-2 - add support for configurable conflict handlers
  * uber - TBUILD-11 - detect file and dir with same name in uber
  * uber - TBUILD-16 - expand default exclusions
  * uber - add support for custom exclusions
* v0.3.0 e418fc9 on Sep 11, 2021
  * Bump to latest tools.deps - 0.12.1036
* v0.2.2 3049217 on Sep 7, 2021
  * unzip - new task to unzip a zip file in a dir
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
