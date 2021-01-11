(ns cloffeine.cache
  (:refer-clojure :exclude [get])
  (:require [cloffeine.common :as common])
  (:import [java.util.concurrent ConcurrentMap]
           [com.github.benmanes.caffeine.cache Cache]))

(defn make-cache
  "Create a LoadingCache. See `cloffeine.common/builder` for settings.
  A semi-persistent mapping from keys to values. Cache entries are manually added
  using get(Object, Function) or put(Object, Object), and are stored in the cache
  until either evicted or manually invalidated."
  (^Cache []
   (make-cache {}))
  (^Cache [settings]
   (let [bldr (common/make-builder settings)]
     (.build bldr))))

(defn get
  "Returns the value associated with the key in this cache, obtaining that value
  from the mappingFunction if necessary."
  [^Cache cache k loading-fn]
  (.get cache k (common/ifn->function loading-fn)))

(defn get-all
  "Returns a map of the values associated with the keys, creating or retrieving
  those values if necessary using 'mapping-fn'."
  [^Cache cache ks mapping-fn]
  (into {} (.getAll cache ks (common/ifn->function mapping-fn))))

(defn get-all-present
  "Returns a map of all the values associated with the keys in this cache."
  [^Cache cache ks]
  (into {} (.getAllPresent cache ks)))

(defn get-if-present
  "Returns the value associated with the key in this cache, or nil if there is
  no cached value for the key."
  [^Cache cache k]
  (.getIfPresent cache k))

(defn invalidate!
  "Disassociates the value cached for the key."
  [^Cache cache k]
  (.invalidate cache k))

(defn invalidate-all!
  "Disassociates all the values in the cache."
  ([^Cache cache]
   (.invalidateAll cache))
  ([^Cache cache ks]
   (.invalidateAll cache ks)))

(defn put! 
  "Associates the value with the key in this cache."
  [^Cache cache k v]
  (.put cache k v))

(defn cleanup
  "Performs any pending maintenance operations needed by the cache."
  [^Cache cache]
  (.cleanUp cache))

(defn estimated-size 
  "Returns the approximate number of entries in this cache."
  [^Cache cache]
  (.estimatedSize cache))

(defn policy
  "Returns access to inspect and perform low-level operations on this cache based
  on its runtime characteristics."
  [^Cache cache]
  (.policy cache))

(defn as-map 
  "Returns a view of the entries stored in this cache as a thread-safe map."
  ^ConcurrentMap [^Cache cache]
  (.asMap cache))

(defn- compute- [^ConcurrentMap m k bi-fn]
  (.compute m k bi-fn))

(defn compute 
  "Attempts to compute a mapping for the specified key and its current mapped
  value (or nil if there is no current mapping).
  For example, to either create or append a String msg to a value mapping:
  `(compute cache \"k\" (fn [k, v] (if (nil? v) msg (str v msg)))`

  See: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html#compute-K-java.util.function.BiFunction-"
  [^Cache cache k remapper-fn]
  (let [bi-fn (common/ifn->bifunction remapper-fn)]
    (-> cache
        as-map
        (compute- k bi-fn))))
