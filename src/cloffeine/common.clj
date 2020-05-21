(ns cloffeine.common
  (:import [com.github.benmanes.caffeine.cache Caffeine CacheLoader AsyncCacheLoader Cache CacheWriter
            Weigher]
           [com.github.benmanes.caffeine.cache.stats CacheStats]
           [java.util.function Function BiFunction]
           [java.util.concurrent TimeUnit]))

(defn- time-unit
  [tu] 
  (case tu
    :ms TimeUnit/MILLISECONDS
    :us TimeUnit/MICROSECONDS
    :s TimeUnit/SECONDS
    :m TimeUnit/MINUTES
    :h TimeUnit/HOURS
    :d TimeUnit/DAYS))

(defn make-builder
  "A builder for the various types of Caffeine's caches (AsyncCache, AsyncLoadingCache, Cache, LoadingCache). The corresponding `create` functions, accept and pass here this config to create the required cache.
  `settings` is a map with the keys (all optional):

  * `:recordStats` `boolean`
  * `:statsCounterSupplier` a `com.github.benmanes.caffeine.cache.stats.StatsCounter`, mutually exclusive with `:recordStats`
  * `:maximumSize` `long` Specifies the maximum number of entries the cache may contain.
  * `:expireAfter` `com.github.benmanes.caffeine.cache.Expiry`
  * `:expireAfterAccess` `long`, references :timeUnit. Specifies that each entry should be automatically removed from the cache once a fixed duration has elapsed after the entry's creation, the most recent replacement of its value, or its last read.
  * `:expireAfterWrite` `long`, references :timeUnit. Specifies that active entries are eligible for automatic refresh once a fixed duration has elapsed after the entry's creation, or the most recent replacement of its value. 
  * `:executor` `java.util.concurrent.Executor` Specifies the executor to use when running asynchronous tasks.
  * `:weakKeys` `boolean`. Specifies that each key (not value) stored in the cache should be wrapped in a WeakReference (by default, strong references are used).
  * `:initialCapacity` 'long'. Sets the minimum total size for the internal data structures.
  * `:softValues` `Boolean` Specifies that each value (not key) stored in the cache should be wrapped in a SoftReference (by default, strong references are used). Softly-referenced objects will be garbage-collected in a globally least-recently-used manner, in response to memory demand.
  * `:ticker` `com.github.benmanes.caffeine.cache.Ticker` Specifies a nanosecond-precision time source for use in determining when entries should be expired or refreshed. By default, System.nanoTime() is used.
  * `:removalListener` `com.github.benmanes.caffeine.cache.RemovalListener` Specifies a listener instance that caches should notify each time an entry is removed for any reason. Each cache created by this builder will invoke this listener as part of the routine maintenance described in the class documentation above.
  * `:weigher` `com.github.benmanes.caffeine.cache.Weigher` Specifies the weigher to use in determining the weight of entries.
  * `:writer` `com.github.benmanes.caffeine.cache.CacheWriter` Specifies a writer instance that caches should notify each time an entry is explicitly created or modified, or removed for any reason.
  * `:timeUnit` ``[:ms :us :s :m :h :d]`` default is `:s`"
  ^Caffeine [settings]
  (let [bldr (Caffeine/newBuilder)
        settings (merge {:timeUnit :s} settings)
        timeUnit (time-unit (:timeUnit settings))]
    (when (and (:recordStats settings)
               (:statsCounterSupplier settings))
      (throw (ex-info "Configuration error. :recordStats and :statsCounterSupplier are mutually exclusive" settings)))
    (cond-> bldr
      (:recordStats settings) (.recordStats)
      (:statsCounterSupplier settings) (.recordStats (:statsCounterSupplier settings))
      (:maximumSize settings) (.maximumSize (int (:maximumSize settings)))
      (:expireAfter settings) (.expireAfter (:expireAfter settings))
      (:expireAfterAccess settings) (.expireAfterAccess (:expireAfterAccess settings) timeUnit)
      (:expireAfterWrite settings) (.expireAfterWrite (:expireAfterWrite settings) timeUnit)
      (:refreshAfterWrite settings) (.refreshAfterWrite (:refreshAfterWrite settings) timeUnit)
      (:executor settings) (.executor (:executor settings))
      (:weakKeys settings) (.weakKeys)
      (:weakValues settings) (.weakValues)
      (:initialCapacity settings) (.initialCapacity (int (:initialCapacity settings)))
      (:softValues settings) (.softValues)
      (:ticker settings) (.ticker (:ticker settings))
      (:removalListener settings) (.removalListener (:removalListener settings))
      (:weigher settings) (.weigher (:weigher settings))
      (:writer settings) (.writer (:writer settings)))))

(defn reify-async-cache-loader
 "A helper for implemeting an `AsyncCacheLoader`.
 
 * `loading-fn` ``(fn [this k executor])``. Asynchronously computes or retrieves the value corresponding to key.
 * `reloading-fn` (optional) ``(fn [this k old-val executor])`` Asynchronously computes or retrieves a replacement value corresponding to an already-cached key. If the replacement value is not found then the mapping will be removed if null is computed. This method is called when an existing cache entry is refreshed by Caffeine.refreshAfterWrite(java.time.Duration), or through a call to LoadingCache.refresh(K).
 "
  ([loading-fn]
   (reify AsyncCacheLoader
     (asyncLoad [_this k executor]
       (loading-fn k executor))))
  ([loading-fn reloading-fn]
   (reify AsyncCacheLoader
     (asyncLoad [_this k executor]
       (loading-fn k executor))
     (asyncReload [_this k v executor]
       (reloading-fn k v executor)))))

(defn reify-cache-loader 
  "A helper for implemeting `CacheLoader`
  
  * `loading-fn `(fn [this k])` Computes or retrieves the value corresponding to key.
  * `relaoding-fn` `(fn [this k old-val]) Computes or retrieves a replacement value corresponding to an already-cached key."
  ([loading-fn]
   (reify CacheLoader
     (load [_this k]
       (loading-fn k))))
  ([loading-fn reloading-fn]
   (reify CacheLoader
     (load [_this k]
       (loading-fn k))
     (reload [_this k v]
       (reloading-fn k v)))))

(defn reify-cache-writer
  "A helper for implementing` CacheWriter`
  
  * `delete-handler` `(fn [this k v removal-cause])` Deletes the value corresponding to the key from the external resource.
  * `write-handler` `(fn [this k v])` Writes the value corresponding to the key to the external resource."
  [delete-handler write-handler]
  (reify CacheWriter
    (delete ^void [this k v removal-cause]
      (delete-handler this k v removal-cause))
    (write ^void [this k v]
      (write-handler this k v))))

(defn reify-weigher 
  "A helper for implemeting `Weigher`
  
  `weigh-fn` `(fn [this k v])` Returns the weight of a cache entry (int). There is no unit for entry weights; rather they are simply relative to each other.
"
  [weigh-fn]
  (reify Weigher
    (weigh ^Int [this k v]
      (weigh-fn this k v))))

(defn ifn->function ^Function [ifn]
  (reify Function
    (apply [_this t]
      (ifn t))))

(defn ifn->bifunction ^BiFunction [ifn]
  (reify BiFunction
    (apply [_this t u]
      (ifn t u))))

(defn cache-stats->map 
  "Convert CacheStats object readonly attributes to a clojure map
  see: https://www.javadoc.io/static/com.github.ben-manes.caffeine/caffeine/2.8.0/com/github/benmanes/caffeine/cache/stats/CacheStats.html
  for actual metrics docs."
  [^CacheStats cs]
  {:averageLoadPenalty (.averageLoadPenalty cs)
   :evictionCount (.evictionCount cs)
   :evictionWeight (.evictionWeight cs)
   :hitCount (.hitCount cs)
   :hitRate (.hitRate cs)
   :loadCount (.loadCount cs)
   :loadFailureCount (.loadFailureCount cs)
   :loadFailureRate (.loadFailureRate cs)
   :loadSuccessCount (.loadSuccessCount cs)
   :missCount (.missCount cs)
   :missRate (.missRate cs)
   :requestCount (.requestCount cs)
   :totalLoadTime (.totalLoadTime cs)})
   
(defn stats "Returns a current snapshot of this cache's cumulative statistics."
  [^Cache c]
  (cache-stats->map (.stats c)))
