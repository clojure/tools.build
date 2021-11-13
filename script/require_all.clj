(ns require-all
  (:require [require-deps]))

(require 'clojure.tools.build.api)
(require 'clojure.tools.build.tasks.compile-clj)
(require 'clojure.tools.build.tasks.copy)
(require 'clojure.tools.build.tasks.create-basis)
(require 'clojure.tools.build.tasks.install)
(require 'clojure.tools.build.tasks.jar)
(require 'clojure.tools.build.tasks.javac)
(require 'clojure.tools.build.tasks.process)
(require 'clojure.tools.build.tasks.uber)
(require 'clojure.tools.build.tasks.write-pom)
