(ns cloffeine.loading-cache
  (:refer-clojure :exclude [get])
  (:require [cloffeine.cache :as cache]
            [cloffeine.common :as common])
  (:import [com.github.benmanes.caffeine.cache Cache CacheLoader LoadingCache]))

(defn make-cache
  "Create a LoadingCache. See `cloffeine.common/builder` for settings.
  A semi-persistent mapping from keys to values. Values are automatically loaded
  by the cache, and are stored in the cache until either evicted or manually
  invalidated."
  (^LoadingCache [^CacheLoader cl]
   (make-cache cl {}))
  (^LoadingCache [^CacheLoader cl settings]
   (let [bldr (common/make-builder settings)]
     (.build bldr cl))))

(def ^{:doc "Returns the value associated with the key in this cache, or nil if
            there is no cached value for the key."}
  get-if-present cache/get-if-present)

(def ^{:doc "Disassociates the value cached for the key."}
  invalidate! cache/invalidate!)

(def ^{:doc "Associates the value with the key in this cache."}
  put! cache/put!)

(defn get
  "Returns the value associated with the key in this cache, obtaining that value
  from CacheLoader.load(Object) if necessary."
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

(defn refresh-all
  "Loads a new value for the keys, asynchronously."
  [^LoadingCache lcache ks]
  (.refreshAll lcache ks))

(defn get-all 
  "Returns a map of the values associated with the keys, creating or retrieving
  those values if necessary."
  [^LoadingCache lcache ks]
  (into {} (.getAll lcache ks)))
