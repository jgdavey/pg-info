(ns com.joshuadavey.pg-info.jdbc
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [com.joshuadavey.pg-info.protocols :as proto]))

(def default-query (slurp (io/resource "com/joshuadavey/pg_info.sql")))

(defprotocol ToVec
  (to-vec [obj]))

(extend-protocol ToVec
  clojure.lang.IPersistentCollection
  (to-vec [obj] (vec obj))

  java.util.List
  (to-vec [obj] (vec obj))

  java.sql.Array
  (to-vec [obj]
    (vec (.getArray obj)))

  nil
  (to-vec [obj] nil))

(defn- normalize-cols [m]
  (-> m
      (update :pkey to-vec)
      (update :dependents to-vec)
      (update :colid to-vec)
      (update :dep-to to-vec)
      (update :dep-from to-vec)
      (update :index-names to-vec)
      (update :dependencies to-vec)))

(defn fetch-pg-info
  ([conn]
   (fetch-pg-info conn default-query))
  ([conn query]
   (jdbc/with-db-connection [conn conn]
     (jdbc/query conn query
                 {:row-fn normalize-cols
                  :identifiers #(-> %
                                    str/lower-case
                                    (str/replace "_" "-"))}))))

(defn index-raw-info [raw]
  (let [name-idx (persistent!
                  (reduce
                   (fn [acc col]
                     (assoc! acc (:colid col) (:fullcolname col)))
                   (transient {})
                   raw))
        oid->table (reduce (fn [m col]
                             (if (contains? m (:table-oid col))
                               m
                               (assoc m (:table-oid col)
                                      (-> col
                                          (select-keys [:table-name :table-schema :table-oid :table-kind])
                                          (assoc :full-name (str (:table-schema col) "." (:table-name col)))
                                          (update :table-kind keyword)))))
                           {}
                           raw)
        tables (vals oid->table)
        table-columns (group-by :table-oid raw)
        table-deps (reduce-kv (fn [m table cols]
                                (assoc m table (into #{} (comp (keep :dependencies)
                                                               cat
                                                               (map ffirst))
                                                     cols)))
                              {}
                              table-columns)]
    {:colid->name name-idx
     :oid->table oid->table
     :tables tables
     :table-columns table-columns
     :table-deps table-deps
     :data raw}))

(defn disabling-referential-integrity [conn table-names f]
  (try
    (jdbc/with-db-transaction [tx conn]
      (doseq [table table-names]
        (jdbc/execute! tx (str "alter table " table " disable trigger all"))))
    (f)
    (finally
      (jdbc/with-db-transaction [tx conn]
        (doseq [table table-names]
         (jdbc/execute! tx (str "alter table " table " enable trigger all")))))))

(defmacro with-no-referential-integrity [index conn & body]
  `(disabling-referential-integrity ~conn (proto/table-names ~index) (fn* [] ~@body)))

(defn clean* [index conn opts]
  (let [all-tables (proto/table-names index)
        only (into (sorted-set) (or (:only opts) all-tables))
        to-clean (set/difference only (:except opts))
        tables (if (:skip-empty? opts)
                 (->> to-clean
                      (map (fn [table]
                             (str "select '" table "' as t where exists (select 1 from " table")")))
                      (str/join " union all ")
                      (jdbc/query conn)
                      (map :t))
                 to-clean)]
    (with-no-referential-integrity index conn
      (case (:strategy opts)
        :truncation
        (when (seq tables)
          (jdbc/execute! conn (str "truncate " (str/join "," tables) " restart identity" (when (:skip-empty? opts) " cascade"))))

        (nil :deletion)
        (jdbc/with-db-transaction [tx conn]
          (doseq [table tables]
            (jdbc/execute! tx (str "delete from " table))))))))

(defrecord JDBCIndex [index-data]
  proto/Index
  (table-names [_]
    (into [] (comp
              (remove #(= :view (:table-kind %)))
              (map :full-name))
          (:tables index-data)))
  (table-dependencies [_]
    (:table-deps index-data))
  (columns [_]
    (:data index-data))
  proto/Cleanable
  (clean [this conn opts]
    (clean* this conn opts)))

(defn index [conn]
  (-> conn
      fetch-pg-info
      index-raw-info
      ->JDBCIndex))
