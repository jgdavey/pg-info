(ns com.joshuadavey.pg-info.protocols)

(defprotocol Index
  (table-names [_])
  (table-dependencies [_])
  (columns [_]))

(defprotocol Cleanable
  (clean [_ conn opts]))
