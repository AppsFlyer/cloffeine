(ns cloffeine.async-loading-cache
  (:refer-clojure :exclude [get])
  (:require [cloffeine.common :as common]
            [cloffeine.async-cache :as acache]
            [cloffeine.loading-cache :as loading-cache])
  (:import [com.github.benmanes.caffeine.cache 
            AsyncCache 
            AsyncLoadingCache 
            AsyncCacheLoader]))

(defn make-cache
  "Create an AsyncLoadingCache. See `cloffeine.common/builder` for settings.
   A semi-persistent mapping from keys to values. Values are automatically loaded by the cache asynchronously, and are stored in the cache until either evicted or manually invalidated.)
Implementations of this interface are expected to be thread-safe, and can be safely accessed by multiple concurrent threads."
  (^AsyncLoadingCache [cache-loader]
   (make-cache cache-loader {}))
  (^AsyncLoadingCache [cache-loader settings]
   (let [bldr (common/make-builder settings)]
     (.buildAsync bldr cache-loader))))

(defn make-cache-async-loader
  "Create a CacheLoader"
  (^AsyncLoadingCache [^AsyncCacheLoader cl]
   (make-cache cl {}))
  (^AsyncLoadingCache [^AsyncCacheLoader cl settings]
   (let [bldr (common/make-builder settings)]
     (.buildAsync bldr cl))))

(defn get
  "Returns the future associated with key in this cache, obtaining that value from AsyncCacheLoader.asyncLoad(K, java.util.concurrent.Executor) if necessary."
  ([^AsyncLoadingCache alcache k]
   (.get alcache k))
  ([^AsyncCache alcache k f]
   (acache/get alcache k f)))

(def get-if-present acache/get-if-present)
(def put! acache/put!)

(defn ->LoadingCache 
  "Returns a view of the entries stored in this cache as a synchronous LoadingCache."
  [^AsyncLoadingCache alcache]
  (.synchronous alcache))

(defn invalidate!
  "Discards any cached value for the key."
  [^AsyncLoadingCache alcache k]
  (-> alcache
      ->LoadingCache
      (loading-cache/invalidate! k)))
