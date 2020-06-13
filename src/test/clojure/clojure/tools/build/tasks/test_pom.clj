(ns clojure.tools.build.tasks.test-pom
  (:require
    [clojure.test :refer :all]
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.build.tasks.pom :as gen-pom]
    [clojure.tools.deps.alpha :as deps])
  (:import
    [java.io File]))

;; simple check that pom gen is working - gen a pom.xml from this deps.edn
(deftest test-pom-gen
  (let [temp-dir (.getParentFile (File/createTempFile "dummy" nil))
        pom (jio/file temp-dir "pom.xml")
        {:keys [root-edn user-edn project-edn]} (deps/find-edn-maps)
        master-edn (deps/merge-edns [root-edn user-edn project-edn])]
    (.delete pom)
    (gen-pom/sync-pom {:basis master-edn
                       :params {:src-pom "pom.xml"
                                :target-dir temp-dir}})
    (is (.exists pom))
    (is (not (str/blank? (slurp pom))))))

;; check that optional deps are marked optional
(deftest test-optional
  (let [temp-dir (.getParentFile (File/createTempFile "dummy" nil))
        pom (jio/file temp-dir "pom.xml")
        master-edn (deps/merge-edns [(:root-edn (deps/find-edn-maps))
                                     {:deps {'org.clojure/core.async {:mvn/version "1.1.587" :optional true}}}])]
    (.delete pom)
    (gen-pom/sync-pom
       {:basis master-edn
        :params {:target-dir temp-dir}})
    (is (.exists pom))
    (let [generated (slurp pom)]
      (is (str/includes? generated "core.async"))
      (is (str/includes? generated "<optional>true</optional>")))))

(comment
  (run-tests)
  )