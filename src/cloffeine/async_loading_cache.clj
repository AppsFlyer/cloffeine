(ns cloffeine.async-loading-cache
  (:refer-clojure :exclude [get])
  (:require [cloffeine.async-cache :as acache]
            [cloffeine.common :as common]
            [cloffeine.loading-cache :as loading-cache])
  (:import [com.github.benmanes.caffeine.cache
            AsyncCache
            AsyncLoadingCache
            AsyncCacheLoader
            CacheLoader]))

(defn make-cache
  "Create an AsyncLoadingCache. See `cloffeine.common/builder` for settings.
  A semi-persistent mapping from keys to values. Values are automatically loaded
  by the cache synchronously, and are stored in the cache until either evicted
  or manually invalidated.
  Implementations of this interface are expected to be thread-safe, and can be
  safely accessed by multiple concurrent threads."
  (^AsyncLoadingCache [^CacheLoader cache-loader]
   (make-cache cache-loader {}))
  (^AsyncLoadingCache [^CacheLoader cache-loader settings]
   (let [bldr (common/make-builder settings)]
     (.buildAsync bldr cache-loader))))

(defn make-cache-async-loader
  "Create an AsyncLoadingCache that uses an AsyncCacheLoader to asynchronously
  load missing entries."
  (^AsyncLoadingCache [^AsyncCacheLoader cl]
   (make-cache-async-loader cl {}))
  (^AsyncLoadingCache [^AsyncCacheLoader cl settings]
   (let [bldr (common/make-builder settings)]
     (.buildAsync bldr cl))))

(defn get
  "Returns the future associated with the key in this cache, obtaining that
  value from AsyncCacheLoader.asyncLoad(K, java.util.concurrent.Executor) if necessary."
  ([^AsyncLoadingCache alcache k]
   (.get alcache k))
  ([^AsyncCache alcache k f]
   (acache/get alcache k f)))

(def ^{:doc "Returns the future associated with key in this cache, or nil if
             there is no cached future for key."}
  get-if-present acache/get-if-present)

(def ^{:doc "Associates value with key in this cache."}
  put! acache/put!)

(defn ->LoadingCache
  "Returns a view of the entries stored in this cache as a synchronous cache."
  [^AsyncLoadingCache alcache]
  (.synchronous alcache))

(defn invalidate!
  "Disassociates the value cached for the key."
  [^AsyncLoadingCache alcache k]
  (-> alcache
      ->LoadingCache
      (loading-cache/invalidate! k)))
