# java.javadocs

A Clojure library for accessing JDK javadocs in your REPL

## Installation

### deps.edn

```clojure
{:deps {org.clojure/java.javadoc {:git/url "https://github.com/clojure/java.javadoc"
                                  :git/sha "b98dfbc6360964ae5831ed21f4b794f05568e8d0"}}}
```

### In the REPL with add-libs

For usage without modifying your project deps:

```clojure
;; This require is only necessary if not in user namespace
(require '[clojure.repl.deps :refer [add-lib]])

(add-lib 'org.clojure/java.javadoc)

(require '[java-javadocs.core :refer [javadoc javadoc-data]])

;; Now you can use it
(javadoc String)
```

### From the Command Line

Invoke directly from the command line, useful for piping into a .md file to display in your editor:

```bash
clojure -Sdeps '{:deps {org.clojure/java.javadoc {:git/url "https://github.com/clojure/java.javadoc" :git/sha "b98dfbc6360964ae5831ed21f4b794f05568e8d0"}}}' \
  -M -e "(require '[java-javadocs.core :refer [javadoc]]) (javadoc String)"
```

## Usage

The core namespace provides two functions:

### javadoc

Prints a markdown formatted version of the javadoc description:

```clojure
(require '[java-javadocs.core :refer [javadoc javadoc-data]])

;; Print class description
(javadoc String)

;; Print all overloads of a method
(javadoc String/valueOf)

;; Specify a specific overload using param-tags
(javadoc ^[char/1] String/valueOf)

;; Use _ to match any type:
(javadoc ^[_ int int] String/.substring)
```

### javadoc-md

Returns structured data instead of printing:

```clojure
(javadoc-data String)
;; => {:classname "java.lang.String"
;;     :class-description-html "..."
;;     :class-description-md "..."
;;     :methods [{:signature "valueOf(int i)"
;;                :description "Returns the string representation..."
;;                :static? true
;;                :clojure-call "^[int] String/valueOf"}
;;               ...]}

(javadoc-data ^[char/1] String/valueOf)
;; => {:classname "java.lang.String"
;;     :class-description-html "..."
;;     :class-description-md "..."
;;     :methods [...]
;;     :selected-method [{:signature "valueOf(char[] data)"
;;                        :description "Returns the string representation..."
;;                        :static? true
;;                        :clojure-call "^[char/1] String/valueOf"
;;                        :method-description-html "..."
;;                        :method-description-md "..."}]}
```

## Requirements

- Java 17+
- Internet required to fetch html javadocs from docs.oracle.com

## Copyright and License

Copyright © 2025

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
