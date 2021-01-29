(ns clojure.tools.build.api)

(defn clean
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.clean/clean) basis params))

(defn compile-clj
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.compile-clj/compile-clj) basis params))

(defn copy
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.copy/copy) basis params))

(defn install
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.install/install) basis params))

(defn javac
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.javac/javac) basis params))

(defn jar
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.jar/jar) basis params))

(defn sync-pom
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.sync-pom/sync-pom) basis params))

(defn uber
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.uber/uber) basis params))

(defn zip
  [basis params]
  ((requiring-resolve 'clojure.tools.build.tasks.zip/zip) basis params))
