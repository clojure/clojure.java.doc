(ns java-javadocs.source-spike)
  ; (:require [clojure.string :as str]
  ;           [clojure.tools.deps :as deps]
  ;           [clojure.java.io :as io])
  ; (:import [java.util.jar JarFile]
  ;          [com.github.javaparser StaticJavaParser ParserConfiguration ParserConfiguration$LanguageLevel]
  ;          [com.github.javaparser.ast CompilationUnit]
  ;          [com.github.javaparser.ast.body ClassOrInterfaceDeclaration MethodDeclaration]
  ;          [com.github.javaparser.ast.nodeTypes NodeWithJavadoc]
  ;          [com.github.javaparser.javadoc Javadoc]
  ;          [com.vladsch.flexmark.html2md.converter FlexmarkHtmlConverter]))

; (set! *warn-on-reflection* true)

; (defn- find-jar-coords [jar-url-str]
  ; (let [libs (:libs (deps/create-basis {:aliases []}))]
  ;   (first (for [[lib-sym lib-info] libs
  ;                path (:paths lib-info)
  ;                :when (str/includes? jar-url-str path)]
  ;            {:protocol :jar
  ;             :lib lib-sym
  ;             :version (select-keys lib-info [:mvn/version])}))))

; (defn- find-javadoc-coords [^Class c]
  ; (let [class-name (.getName c)
  ;       url (.getResource c (str (.getSimpleName c) ".class"))]
  ;   (merge
  ;     {:class-name class-name}
  ;     (case (.getProtocol url)
  ;       "jar" (find-jar-coords (.toString url))
  ;       "jrt" {:protocol :jrt
  ;              :lib 'java/java
  ;              :version {:mvn/version (System/getProperty "java.version")}}
  ;       "file" nil))))

; (defn- find-source-jar-path [{:keys [lib version]}]
  ; (let [group-path (str/replace (namespace lib) "." "/")
  ;       artifact (name lib)
  ;       version-str (:mvn/version version)
  ;       jar-name (str artifact "-" version-str "-sources.jar")
  ;       home (System/getProperty "user.home")]
  ;   (str home "/.m2/repository/" group-path "/" artifact "/" version-str "/" jar-name)))

; (defn- download-source-jar [{:keys [lib version] :as coords}]
  ; (let [source-jar-path (find-source-jar-path coords)]
  ;   (when-not (.exists (io/file source-jar-path))
  ;     (deps/resolve-deps {:deps {(symbol (str lib "$sources")) version}} nil))
  ;   source-jar-path))

; (defn- source-path [class-name]
  ; (str (str/replace class-name "." "/") ".java"))

; (defn- extract-source-from-jar [jar-path class-name]
  ; (with-open [jar (JarFile. ^String jar-path)]
  ;   (with-open [is (.getInputStream jar (.getJarEntry jar (source-path class-name)))]
  ;     (slurp is))))

; (defn- extract-source-from-jrt [class-name]
  ; (let [src-zip (str (System/getProperty "java.home") "/lib/src.zip")
  ;       entry-path (str "java.base/" (source-path class-name))]
  ;   (with-open [jar (JarFile. ^String src-zip)]
  ;     (with-open [is (.getInputStream jar (.getJarEntry jar entry-path))]
  ;       (slurp is)))))

; (def highest-java-language-level
  ; (let [levels (ParserConfiguration$LanguageLevel/values)
  ;       sorted (sort-by #(.ordinal ^ParserConfiguration$LanguageLevel %) > levels)]
  ;   (first sorted)))

; (defn- parse-java-source [^String source-code]
  ; (let [config (.setLanguageLevel (ParserConfiguration.) highest-java-language-level)]
  ;   (StaticJavaParser/setConfiguration config)
  ;   (StaticJavaParser/parse source-code)))

; (defn- find-class-declaration [^CompilationUnit cu class-name]
  ; (let [simple-name (last (str/split class-name #"\."))]
  ;   (first (filter #(= simple-name (.getNameAsString ^ClassOrInterfaceDeclaration %))
  ;                  (.findAll cu ClassOrInterfaceDeclaration)))))

; (defn- find-method [^ClassOrInterfaceDeclaration class-decl method-name]
  ; (first (filter #(= method-name (.getNameAsString ^MethodDeclaration %))
  ;                (.getMethods class-decl))))

; (defn- extract-javadoc [^NodeWithJavadoc node]
  ; (when-let [javadoc-opt (.getJavadoc node)]
  ;   (when (.isPresent javadoc-opt)
  ;     (.toText ^Javadoc (.get javadoc-opt)))))

; (defn- first-sentence [text]
  ; (when text
  ;   (first (str/split text #"\.\s"))))

; (defn- inherits-javadoc? [text]
  ; (and text (str/includes? text "{@inheritDoc}")))

; (defn- build-import-map [^CompilationUnit cu]
  ; (let [imports (.getImports cu)]
  ;   (reduce (fn [acc ^com.github.javaparser.ast.ImportDeclaration import-decl]
  ;             (let [import-name (.getNameAsString import-decl)]
  ;               (if (.isAsterisk import-decl)
  ;                 acc
  ;                 (let [simple-name (last (str/split import-name #"\."))]
  ;                   (assoc acc simple-name import-name)))))
  ;           {}
  ;           imports)))

; (defn- resolve-type-name [import-map package-name simple-name]
  ; (cond
  ;   (str/includes? simple-name ".") simple-name
  ;   (get import-map simple-name) (get import-map simple-name)
  ;   package-name (str package-name "." simple-name)
  ;   :else (str "java.lang." simple-name)))

; (defn- get-parent-types [^CompilationUnit cu ^ClassOrInterfaceDeclaration class-decl]
  ; (let [import-map (build-import-map cu)
  ;       package-name (when-let [pkg (.orElse ^java.util.Optional (.getPackageDeclaration cu) nil)]
  ;                      (.getNameAsString pkg))
  ;       extended (.getExtendedTypes class-decl)
  ;       implemented (.getImplementedTypes class-decl)
  ;       all-parents (concat extended implemented)]
  ;   (map (fn [^com.github.javaparser.ast.type.ClassOrInterfaceType parent]
  ;          (let [name (.getNameAsString parent)
  ;                base-name (first (str/split name #"<"))]
  ;            (resolve-type-name import-map package-name base-name)))
  ;        all-parents)))

; (defn- print-method-summary [^ClassOrInterfaceDeclaration class-decl]
  ; (let [methods (.getMethods class-decl)
  ;       public-methods (filter #(.isPublic ^MethodDeclaration %) methods)]
  ;   (doseq [^MethodDeclaration method public-methods]
  ;     (let [method-name (.getNameAsString method)
  ;           javadoc-text (extract-javadoc method)
  ;           summary (first-sentence javadoc-text)]
  ;       (when summary
  ;         (println (str "* " method-name " - " summary)))))))

; (defn- handle-error [^Exception e ^Class class]
  ; (if (str/includes? (.getMessage e) "Could not find artifact")
  ;   (println "No source JAR available for:" class)
  ;   (println "Error:" (.getMessage e))))

; (defn- print-member-javadoc [cu class-decl member-name]
  ; (let [javadoc-text (extract-javadoc (find-method class-decl member-name))]
  ;   (if (inherits-javadoc? javadoc-text)
  ;     (let [parents (get-parent-types cu class-decl)]
  ;       (println "Inherited from parent. See:")
  ;       (doseq [parent parents]
  ;         (println (str "  " parent "/" member-name))))
  ;     (println (.convert (.build (FlexmarkHtmlConverter/builder)) ^String javadoc-text)))))

; (defn- print-class-javadoc [class-decl]
  ; (when-let [class-javadoc (extract-javadoc class-decl)]
  ;   (println (.convert (.build (FlexmarkHtmlConverter/builder)) ^String class-javadoc))
  ;   (println "\n--- Methods ---\n"))
  ; (print-method-summary class-decl))

; (defn javadoc* [^Class class member-name]
  ; (try
  ;   (let [coords (find-javadoc-coords class)
  ;         class-name (:class-name coords)
  ;         source-code (case (:protocol coords)
  ;                       :jrt (extract-source-from-jrt class-name)
  ;                       :jar (extract-source-from-jar (download-source-jar coords) class-name))
  ;         cu (parse-java-source source-code)
  ;         class-decl (find-class-declaration cu class-name)]
  ;     (if member-name
  ;       (print-member-javadoc cu class-decl member-name)
  ;       (print-class-javadoc class-decl)))
  ;   (catch Exception e
  ;     (handle-error e class))))

; (defmacro javadoc
  ; "Get javadoc for a class or class member.
  ; Usage:
  ;   (javadoc String)                         ; class javadoc
  ;   (javadoc String/valueOf)                 ; method javadoc
  ;   (javadoc java.lang.String)               ; fully qualified class
  ;   (javadoc java.lang.String/valueOf)       ; fully qualified with method
  ;   (javadoc StringUtils/isEmpty)            ; 3rd party class method"
  ; [class-or-member]
  ; (let [class-or-member-str (str class-or-member)
  ;       parts (str/split class-or-member-str #"/")
  ;       class-sym (symbol (first parts))
  ;       member-name (second parts)]
  ;   `(javadoc* ~class-sym ~member-name)))
