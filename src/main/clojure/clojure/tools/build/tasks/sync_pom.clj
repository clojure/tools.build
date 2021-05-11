;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.sync-pom
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.data.xml :as xml]
    [clojure.data.xml.tree :as tree]
    [clojure.data.xml.event :as event]
    [clojure.zip :as zip]
    [clojure.tools.deps.alpha.util.maven :as maven]
    [clojure.tools.deps.alpha.util.io :refer [printerrln]]
    [clojure.tools.build.task.api :as tapi]
    [clojure.tools.build.task.file :as file])
  (:import [java.io File Reader]
           [clojure.data.xml.node Element]))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defn- to-dep
  [[lib {:keys [mvn/version exclusions optional] :as coord}]]
  (let [[group-id artifact-id classifier] (maven/lib->names lib)]
    (if version
      (cond->
        [::pom/dependency
         [::pom/groupId group-id]
         [::pom/artifactId artifact-id]
         [::pom/version version]]

        classifier
        (conj [::pom/classifier classifier])

        (seq exclusions)
        (conj [::pom/exclusions
               (map (fn [excl]
                      [::pom/exclusion
                       [::pom/groupId (or (namespace excl) (name excl))]
                       [::pom/artifactId (name excl)]])
                 exclusions)])

        optional
        (conj [::pom/optional "true"]))
      (printerrln "Skipping coordinate:" coord))))

(defn- gen-deps
  [deps]
  [::pom/dependencies
   (map to-dep deps)])

(defn- gen-source-dir
  [path]
  [::pom/sourceDirectory path])

(defn- to-resource
  [resource]
  [::pom/resource
   [::pom/directory resource]])

(defn- gen-resources
  [rpaths]
  [::pom/resources
   (map to-resource rpaths)])

(defn- to-repo
  [[name repo]]
  [::pom/repository
   [::pom/id name]
   [::pom/url (:url repo)]])

(defn- gen-repos
  [repos]
  [::pom/repositories
   (map to-repo repos)])

(defn- gen-pom
  [{:keys [deps src-paths resource-paths repos group artifact version]
    :or {version "0.1.0"}}]
  (let [[path & paths] src-paths]
    (xml/sexp-as-element
      [::pom/project
       {:xmlns "http://maven.apache.org/POM/4.0.0"
        (keyword "xmlns:xsi") "http://www.w3.org/2001/XMLSchema-instance"
        (keyword "xsi:schemaLocation") "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"}
       [::pom/modelVersion "4.0.0"]
       [::pom/packaging "jar"]
       [::pom/groupId group]
       [::pom/artifactId artifact]
       [::pom/version version]
       [::pom/name artifact]
       (gen-deps deps)
       (when (or path (seq resource-paths))
         (when (seq paths) (apply printerrln "Skipping paths:" paths))
         [::pom/build
          (when path (gen-source-dir path))
          (when (seq resource-paths) (gen-resources resource-paths))])
       (gen-repos repos)])))

(defn- make-xml-element
  [{:keys [tag attrs] :as node} children]
  (with-meta
    (apply xml/element tag attrs children)
    (meta node)))

(defn- xml-update
  [root tag-path replace-node]
  (let [z (zip/zipper xml/element? :content make-xml-element root)]
    (zip/root
      (loop [[tag & more-tags :as tags] tag-path, parent z, child (zip/down z)]
        (if child
          (if (= tag (:tag (zip/node child)))
            (if (seq more-tags)
              (recur more-tags child (zip/down child))
              (zip/edit child (constantly replace-node)))
            (if-let [next-sibling (zip/right child)]
              (recur tags parent next-sibling)
              (if (seq more-tags)
                (let [new-parent (zip/append-child parent (xml/sexp-as-element tag))
                      new-child (zip/rightmost (zip/down new-parent))]
                  (recur more-tags new-child (zip/down new-child)))
                (zip/append-child parent replace-node))))
          (if (seq more-tags)
            (let [new-parent (zip/append-child parent (xml/sexp-as-element tag))
                  new-child (zip/rightmost (zip/down new-parent))]
              (recur more-tags new-child (zip/down new-child)))
            (zip/append-child parent replace-node)))))))

(defn- replace-deps
  [pom deps]
  (xml-update pom [::pom/dependencies] (xml/sexp-as-element (gen-deps deps))))

(defn- replace-paths
  [pom [path & paths]]
  (if path
    (do
      (when (seq paths) (apply printerrln "Skipping paths:" paths))
      (xml-update pom [::pom/build ::pom/sourceDirectory] (xml/sexp-as-element (gen-source-dir path))))
    pom))

(defn- replace-resources
  [pom resource-paths]
  (if (seq resource-paths)
    (xml-update pom [::pom/build ::pom/resources] (xml/sexp-as-element (gen-resources resource-paths)))
    pom))

(defn- replace-repos
  [pom repos]
  (if (seq repos)
    (xml-update pom [::pom/repositories] (xml/sexp-as-element (gen-repos repos)))
    pom))

(defn- replace-lib
  [pom lib]
  (if lib
    (-> pom
      (xml-update [::pom/groupId] (xml/sexp-as-element [::pom/groupId (namespace lib)]))
      (xml-update [::pom/artifactId] (xml/sexp-as-element [::pom/artifactId (name lib)]))
      (xml-update [::pom/name] (xml/sexp-as-element [::pom/name (name lib)])))
    pom))

(defn- replace-version
  [pom version]
  (if version
    (xml-update pom [::pom/version] (xml/sexp-as-element [::pom/version version]))
    pom))

(defn- parse-xml
  [^Reader rdr]
  (let [roots (tree/seq-tree event/event-element event/event-exit? event/event-node
                (xml/event-seq rdr {:include-node? #{:element :characters :comment}
                                    :skip-whitespace true}))]
    (first (filter #(instance? Element %) (first roots)))))

(defn sync-pom
  [params]
  (let [{:build/keys [basis project-dir output-dir]} params
        {:keys [deps :mvn/repos]} basis
        src-pom (or (tapi/resolve-param basis params :build/src-pom) "pom.xml")
        src-pom-file (jio/file project-dir src-pom)
        lib (tapi/resolve-param basis params :build/lib)
        version (tapi/resolve-param basis params :build/version)
        clj-paths (tapi/resolve-param basis params :build/clj-paths)
        resolved-paths (flatten (map #(tapi/maybe-resolve-param basis params %) clj-paths))
        resource-paths (tapi/resolve-param basis params :build/resource-paths)
        resolved-resource-paths (flatten (map #(tapi/maybe-resolve-param basis params %) resource-paths))
        repos (remove #(= "https://repo1.maven.org/maven2/" (-> % val :url)) repos)
        pom (if (.exists src-pom-file)
              (with-open [rdr (jio/reader src-pom-file)]
                (-> rdr
                  parse-xml
                  (replace-deps deps)
                  (replace-paths resolved-paths)
                  (replace-resources resolved-resource-paths)
                  (replace-repos repos)
                  (replace-lib lib)
                  (replace-version version)))
              (gen-pom
                (cond->
                  {:deps deps
                   :src-paths resolved-paths
                   :resource-paths resolved-resource-paths
                   :repos repos
                   :group (namespace lib)
                   :artifact (name lib)}
                  version (assoc :version version))))
        pom-dir (file/ensure-dir (jio/file output-dir (tapi/resolve-param basis params :build/pom-dir)))]
    (spit (jio/file pom-dir "pom.xml") (xml/indent-str pom))
    (spit (jio/file pom-dir "pom.properties")
      (str/join (System/lineSeparator)
        ["# Generated by org.clojure/tools.build"
         (format "# %tc" (java.util.Date.))
         (format "version=%s" version)
         (format "groupId=%s" (namespace lib))
         (format "artifactId=%s" (name lib))]))
    params))
