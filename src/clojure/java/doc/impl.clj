(ns clojure.java.doc.impl
  (:require
    [clojure.string :as str])
  (:import [com.vladsch.flexmark.html2md.converter FlexmarkHtmlConverter]
           [org.jsoup Jsoup]))

(set! *warn-on-reflection* true)

(defn- check-java-version [^String version-str]
  (let [version (Integer/parseInt version-str)
        min-version 17]
    (when (< version min-version)
      (throw (ex-info
               (str "Java " min-version " or higher is required. Current version: " version-str)
               {:current-version version-str
                :minimum-version min-version})))))

(defn- javadoc-url [^String classname]
  (let [java-version (System/getProperty "java.specification.version")
        _ (check-java-version java-version)
        classname (str/replace classname #"\$.*" "")
        klass (Class/forName classname)
        module-name (.getName (.getModule klass))
        url-path (.replace classname \. \/)]
    (str "https://docs.oracle.com/en/java/javase/" java-version "/docs/api/" module-name "/" url-path ".html")))

(defn- html-to-md [^String html]
  (.convert ^FlexmarkHtmlConverter (.build (FlexmarkHtmlConverter/builder)) html))

(defn- resolve-class-name [class-part]
  (if-let [class-sym (resolve (symbol class-part))]
    (.getName ^Class class-sym)
    (throw (ex-info (str "Cannot resolve class: " class-part) {:class-name class-part}))))

(defn- extract-params
  "extract parameter types from a method signature: valueOf(char[] data) -> [char[]]"
  [signature]
  (let [params-str (->> signature
                        (drop-while #(not= % \())
                                    rest
                                    (take-while #(not= % \)))
                        (apply str))]
    (when-not (str/blank? params-str)
      (mapv #(first (str/split (str/trim %) #"\s+"))
            (str/split params-str #",")))))

(defn- get-method-detail [^org.jsoup.nodes.Document doc method]
  (let [method-signature (:signature method)
        method-name (first (str/split method-signature #"\("))
        param-types (extract-params method-signature)
        method-id (if param-types
                    (str method-name "(" (str/join "," param-types) ")")
                    (str method-name "()"))
        detail-section (.selectFirst doc (str "section[id='" method-id "']"))]
    (if detail-section
      (let [method-html (.outerHtml detail-section)]
        (assoc method
          :method-description-html method-html
          :method-description-md (html-to-md method-html)))
      method)))

(defn- expand-array-syntax
  "expands array syntax for matching javadoc format: String/2 -> String[][]"
  [type-str]
  (cond
    ;; Clojure array syntax: String/2 -> String[][]
    (re-find #"/\d+$" type-str) (let [[base-type dims] (str/split type-str #"/")
                                      array-suffix (apply str (repeat (Integer/parseInt dims) "[]"))]
                                  (str base-type array-suffix))
    ;; varargs: CharSequence... -> CharSequence[]
    (str/ends-with? type-str "...") (str/replace type-str #"[.]{3}$" "[]")
    :else type-str))

(defn- params-match?
  "check if param-tags match the parameters exactly by count and type, supports wildcard _"
  [sig-types param-tags]
  (when sig-types
    (let [param-strs (mapv str param-tags)
          expanded-sig-types (mapv expand-array-syntax sig-types)
          expanded-param-strs (mapv expand-array-syntax param-strs)]
      (and (= (count expanded-sig-types) (count expanded-param-strs))
           (every? (fn [[sig-type param-str]]
                     (or (= param-str "_")
                         (= sig-type param-str)))
                   (map vector expanded-sig-types expanded-param-strs))))))

(defn- method-matches? [signature method-name param-tags]
  (and (str/starts-with? signature method-name)
       (or (nil? param-tags)
           (params-match? (extract-params signature) param-tags))))

(defn- filter-methods [all-methods method-name param-tags]
  (filterv #(method-matches? (:signature %) method-name param-tags) all-methods))

(defn- compress-array-syntax
  "java to clojure param-tag syntax: String[][] -> String/2"
  [java-type]
  (cond
    ;; arrays: String[][] -> String/2
    (str/includes? java-type "[]") (let [base-type (str/replace java-type #"\[\]" "")
                                         dims (count (re-seq #"\[" java-type))]
                                     (str base-type "/" dims))
    ;; varargs: Object... -> Object/1
    (str/ends-with? java-type "...") (str/replace java-type #"[.]{3}$" "/1")
    :else java-type))

(defn- clojure-call-syntax
  "javadoc signature to clojure param-tag syntax: valueOf(char[] data) -> ^[char/1] String/valueOf"
  [class-part method-signature is-static?]
  (let [method-name (first (str/split method-signature #"\("))
        param-types (extract-params method-signature)
        separator (if is-static? "/" "/.")]
    (if param-types
      (let [clojure-types (mapv compress-array-syntax param-types)]
        (str "^[" (str/join " " clojure-types) "] " class-part separator method-name))
      (str class-part separator method-name))))

(defn parse-javadoc
  "parse the javadoc HTML for a class or method into a data structure:
  {:classname 'java.lang.String'
   :class-description-html '...'
   :class-description-md '...'
   :methods [...]
   :selected-method [{:signature 'valueOf(char[] data)'
                      :description 'Returns the string representation...'
                      :static? true
                      :clojure-call '^[char/1] String/valueOf'
                      :method-description-html '...'
                      :method-description-md '...'}]}"
  [s param-tags]
  (let [[class-part method-part] (str/split s #"/\.?" 2)
        class-name (resolve-class-name class-part)
        doc (Jsoup/parse (slurp (javadoc-url class-name)))
        class-desc-section (.selectFirst doc "section.class-description")
        method-rows (.select doc "div.method-summary-table.col-second")
        all-methods (vec (for [^org.jsoup.nodes.Element method-div method-rows]
                           (let [desc-div ^org.jsoup.nodes.Element (.nextElementSibling method-div)
                                 signature (.text (.select method-div "code"))
                                 modifier-div ^org.jsoup.nodes.Element (.previousElementSibling method-div)
                                 modifier-html (when modifier-div (.html modifier-div))
                                 is-static? (and modifier-html (str/includes? modifier-html "static"))]
                             {:signature signature
                              :description (.text (.select desc-div ".block"))
                              :static? is-static?
                              :clojure-call (clojure-call-syntax class-part signature is-static?)})))
        class-html (.outerHtml class-desc-section)
        result {:classname class-name
                :class-description-html class-html
                :class-description-md (html-to-md class-html)
                :methods all-methods}]
    (if method-part
      (let [filtered (filter-methods all-methods method-part param-tags)]
        (assoc result :selected-method
               (mapv #(get-method-detail doc %) filtered)))
      result)))

(defn print-javadoc [{:keys [class-description-md selected-method]}]
  (if selected-method
    (doseq [{:keys [method-description-md]} selected-method]
      (println method-description-md))
    (println class-description-md)))
