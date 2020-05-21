(ns cloffeine.loading-cache
  (:refer-clojure :exclude [get])
  (:require [cloffeine.common :as common]
            [cloffeine.cache :as cache])
  (:import [com.github.benmanes.caffeine.cache Cache LoadingCache CacheLoader]))

(defn make-cache
  "Create an AsyncLoadingCache. See `cloffeine.common/builder` for settings.
  A semi-persistent mapping from keys to values. Values are automatically loaded by the cache, and are stored in the cache until either evicted or manually invalidated.
Implementations of this interface are expected to be thread-safe, and can be safely accessed by multiple concurrent threads."
  (^LoadingCache [^CacheLoader cl]
   (make-cache cl {}))
  (^LoadingCache [^CacheLoader cl settings]
   (let [bldr (common/make-builder settings)]
     (.build bldr cl))))

(def get-if-present cache/get-if-present)

(def invalidate! cache/invalidate!)

(def put! cache/put!)

(defn get
  "Returns the value associated with the key in this cache, obtaining that value from CacheLoader.load(Object) if necessary."
  ([^LoadingCache lcache k]
   (.get lcache k))
  ([^Cache lcache k loading-fn]
   (cache/get lcache k loading-fn)))

(defn cleanup
  "Performs any pending maintenance operations needed by the cache."
  [^Cache lcache]
  (cache/cleanup lcache))

(defn refresh 
  "Loads a new value for the key, asynchronously."
  [^LoadingCache lcache k]
  (.refresh lcache k))

(defn get-all 
  "Returns a map of the values associated with the keys, creating or retrieving those values if necessary."
  [^LoadingCache lcache ks]
  (into {} (.getAll lcache ks)))
