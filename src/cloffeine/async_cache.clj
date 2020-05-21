(ns cloffeine.async-cache
  (:refer-clojure :exclude [get])
  (:require [cloffeine.common :as common]
            [cloffeine.cache :as cache])
  (:import [com.github.benmanes.caffeine.cache AsyncCache]))

(defn make-cache
  "Create an AsyncCache. See `cloffeine.common/builder` for settings.
  A semi-persistent mapping from keys to values. Values are automatically loaded by the cache asynchronously, and are stored in the cache until either evicted or manually invalidated.
Implementations of this interface are expected to be thread-safe, and can be safely accessed by multiple concurrent threads."
  (^AsyncCache []
   (make-cache {}))
  (^AsyncCache [settings]
   (let [bldr (common/make-builder settings)]
     (.buildAsync bldr))))

(defn get
  "Returns the future associated with key in this cache, obtaining that value from mappingFunction if necessary."
  [^AsyncCache acache k f]
  (.get acache k (common/ifn->function f)))

(defn get-if-present "Returns the future associated with key in this cache, or null if there is no cached future for key."
  [^AsyncCache acache k]
    ;; TODO: acache can return nil. always wrap in future?
  (.getIfPresent acache k))

(defn put! 
  "Associates value with key in this cache."
  [^AsyncCache acache k future-v]
  (.put acache k future-v))

(defn ->Cache
  "Returns a view of the entries stored in this cache as a synchronous Cache."
  [^AsyncCache acache]
  (.synchronous acache))

(defn invalidate!
  "Discards any cached value for the key."
  [^AsyncCache acache k]
  (-> acache
      ->Cache
      (cache/invalidate! k)))
