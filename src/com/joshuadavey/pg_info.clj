(ns com.joshuadavey.pg-info
  (:require
   [com.joshuadavey.pg-info.jdbc]
   [com.joshuadavey.pg-info.protocols :as proto]))

(defn jdbc-index [conn]
  ((requiring-resolve 'com.joshuadavey.pg-info.jdbc/index) conn))

(defn clean
  ([conn]
   (clean conn {}))
  ([conn opts]
   (clean conn opts (jdbc-index conn)))
  ([conn opts cleanable]
   (proto/clean cleanable conn opts)))

(defn clean-after-fixture [conn opts f]
  (fn []
    (f)
    (clean conn opts)))

(defn clean-before-fixture [conn opts f]
  (fn []
    (clean conn opts)
    (f)))
