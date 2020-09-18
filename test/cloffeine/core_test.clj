(ns cloffeine.core-test 
  (:require [cloffeine.common :as common]
            [cloffeine.cache :as cache]
            [cloffeine.async-cache :as async-cache]
            [cloffeine.loading-cache :as loading-cache]
            [cloffeine.async-loading-cache :as async-loading-cache]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [promesa.core :as p]
            [clojure.string :as s])
  (:import [com.google.common.testing FakeTicker]
           [com.github.benmanes.caffeine.cache Ticker]
           [java.util.concurrent TimeUnit]
           [java.util.logging Logger Level]))

(defn configure-logger [test-fn]
  (let [logger (Logger/getLogger "com.github.benmanes.caffeine")
        initial-level (.getLevel logger)]
    (.setLevel logger (Level/SEVERE))
    (test-fn)
    (.setLevel logger initial-level)))

(use-fixtures :each configure-logger)

(defn- reify-ticker [^FakeTicker ft]
  (reify Ticker
    (read [_this]
      (.read ft))))

(deftest manual
  (let [cache (cache/make-cache)]
    (is (= 0 (cache/estimated-size cache)))
    (cache/put! cache :key :v)
    (is (= 1 (cache/estimated-size cache)))
    (is (= :v (cache/get cache :key name)))
    (cache/invalidate! cache :key)
    (is (= "key" (cache/get cache :key name)))
    (is (= {:key "key"}
           (cache/get-all cache [:key] (fn [ks] (reduce 
                                                  (fn [res k]
                                                    (assoc res k (name k)))
                                                  {}
                                                  ks)))))
    (cache/invalidate-all! cache)
    (is (= 0 (cache/estimated-size cache)))))

(deftest loading
  (let [loads (atom 0)
        cl (common/reify-cache-loader (fn [k]
                                        (swap! loads inc)
                                        (name k)))
        lcache (loading-cache/make-cache cl {:recordStats true})]
    (loading-cache/put! lcache :key :v)
    (is (= :v (loading-cache/get lcache :key)))
    (is (= 1 (:hitCount (common/stats lcache))))
    (is (= 1 (:requestCount (common/stats lcache))))
    (is (= 0 @loads))
    (loading-cache/invalidate! lcache :key)
    (is (= "key" (loading-cache/get lcache :key)))
    (is (= 2 (:requestCount (common/stats lcache))))
    (is (= 1 (:loadCount (common/stats lcache))))
    (is (= 0.5 (:hitRate (common/stats lcache))))
    (is (= 0.5 (:missRate (common/stats lcache))))
    (is (= 1 @loads))
    (is (= "key" (loading-cache/get lcache :key name)))
    (is (= 1 @loads))
    (is (= "key" (cache/get lcache :key name)))
    (is (= 1 @loads))
    (cache/invalidate! lcache :key)
    (is (= "key" (cache/get lcache :key name)))
    (is (= 1 @loads))))

(deftest loading-exceptions
  (let [loads (atom 0)
        throw? (atom false)
        load-nil? (atom false)
        cl (common/reify-cache-loader (fn [k]
                                        (cond
                                          @throw?  (throw (ex-info "fail" {}))
                                          @load-nil? nil
                                          :else (do
                                                  (swap! loads inc)
                                                  (name k)))))
        ticker (FakeTicker.)
        lcache (loading-cache/make-cache cl {:refreshAfterWrite 10
                                             :timeUnit :s
                                             :ticker (reify-ticker ticker)})]
    (loading-cache/put! lcache :key :v)
    (testing "successful get, existing key"
      (is (= :v (loading-cache/get lcache :key)))
      (is (= 0 @loads)))
    (testing "loading a missing key"
      (loading-cache/invalidate! lcache :key)
      (is (= "key" (loading-cache/get lcache :key)))
      (is (= 1 @loads)))
    (testing "fail to load a missing key throws exception"
      (reset! throw? true)
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"fail"
                            (loading-cache/get lcache :missing-key)))
      (is (= 1 @loads)))
    (testing "fail to load an expired key"
      (is (true? @throw?))
      (.advance ticker 11 TimeUnit/SECONDS)
      (is (= "key" (loading-cache/get lcache :key)))
      (is (= 1 @loads))
      (is (= "key" (loading-cache/get lcache :key)))
      (is (= 1 @loads)))
    (testing "loading a missing key but loader returns nil. nil returns, but mapping is not changed"
      (reset! throw? false)
      (reset! load-nil? true)
      (is (nil? (loading-cache/get lcache :not-there)))
      (is (= [:key] (keys (.asMap lcache)))))))

(deftest get-if-present
  (let [cache (cache/make-cache)]
    (cache/put! cache :key "v")
    (cache/put! cache :key2 "v2")
    (is (= "v" (cache/get-if-present cache :key)))
    (is (= {:key "v"} (cache/get-all-present cache [:key "v2"])))
    (is (nil? (cache/get-if-present cache :non-existent))))
  (let [loading-cache (loading-cache/make-cache (common/reify-cache-loader str))]
    (loading-cache/put! loading-cache :key "v")
    (is (= (loading-cache/get-if-present loading-cache :key) "v"))
    (loading-cache/invalidate! loading-cache :key)
    (is (nil? (loading-cache/get-if-present loading-cache :key)))))

(deftest refresh-test
  (let [loads (atom 0)
        reloads (atom 0)
        cl (common/reify-cache-loader 
             (fn [k]
                 (swap! loads inc)
                 (name k))
             (fn [k _v]
                 (swap! reloads inc)
                 (name k)))
        lcache (loading-cache/make-cache cl)]
    (is (= "key" (loading-cache/get lcache :key)))
    (is (= 1 @loads))
    (loading-cache/refresh lcache :key)
    (Thread/sleep 10)
    (is (= 1 @reloads))
    (is (= 1 @loads))))

(deftest get-async
  (let [acache (async-cache/make-cache)]
    (async-cache/put! acache :key (p/resolved :v))
    (is (= :v @(async-cache/get acache :key name)))
    (async-cache/invalidate! acache :key)
    (is (= "key" @(async-cache/get acache :key name))))
  (let [alcache (async-loading-cache/make-cache (common/reify-cache-loader name))]
    (async-cache/put! alcache :key (p/resolved :v))
    (is (= :v @(async-loading-cache/get alcache :key name)))
    (async-cache/invalidate! alcache :key)
    (is (= "key" @(async-loading-cache/get alcache :key name)))))

(deftest get-if-present-async
  (let [acache (async-cache/make-cache)]
    (is (nil? (async-cache/get-if-present acache :key)))
    (async-cache/put! acache :key (p/resolved :v))
    (is (= :v @(async-cache/get-if-present acache :key))))
  (let [loads (atom 0)
        cl (common/reify-cache-loader (fn [k]
                                          (swap! loads inc)
                                          (name k)))
        alcache (async-loading-cache/make-cache cl)]
    (is (nil? (async-loading-cache/get-if-present alcache :key)))
    (async-loading-cache/put! alcache :key (p/resolved :v))
    (is (= :v @(async-loading-cache/get-if-present alcache :key)))
    (is (= 0 @loads))))

(deftest get-async-loading
  (let [loads (atom 0)
        cl (common/reify-cache-loader (fn [k]
                                        (swap! loads inc)
                                        (name k)))
        alcache (async-loading-cache/make-cache cl)]
    (is (nil? (async-loading-cache/get-if-present alcache :key)))
    (is (= 0 @loads))
    (is (= "key" @(async-loading-cache/get alcache :key)))
    (is (= 1 @loads))
    (async-loading-cache/invalidate! alcache :key)
    (is (= "key" @(async-loading-cache/get alcache :key)))
    (is (= 2 @loads))
    (let [alcache2 (async-loading-cache/->LoadingCache alcache)]
      (is (= "key" (loading-cache/get alcache2 :key)))
      (is (= 2 @loads))
      (is (= "key2" (loading-cache/get alcache2 :key2)))
      (is (= 3 @loads)))))

(deftest auto-refresh
  (let [loads (atom 0)
        reloads (atom 0)
        reload-fails? (atom false)
        db (atom {:key 17})
        cl (common/reify-cache-loader
             (fn [k]
               (swap! loads inc)
               (get @db k))
             (fn [k _old-v]
               (swap! reloads inc)
               (if @reload-fails?
                 (throw (Exception. (format "reload failed for key: %s" k)))
                 (get @db k))))
        ticker (FakeTicker.)
        lcache (loading-cache/make-cache cl {:refreshAfterWrite 10
                                             :timeUnit :s
                                             :ticker (reify-ticker ticker)})]
    (testing "no key, load succeeds"
      (is (= 17 (loading-cache/get lcache :key)))
      (is (= 1 @loads))
      (is (= 0 @reloads)))
    (testing "key exists, just return from cache"
      (is (= 17 (loading-cache/get lcache :key)))
      (is (= 1 @loads))
      (is (= 0 @reloads)))
    (testing "key needs to be refreshed"
      (swap! db assoc :key 42)
      (.advance ticker 11 TimeUnit/SECONDS)
      (is (= 17 (loading-cache/get lcache :key)))
      (is (= 1 @loads))
      (loading-cache/cleanup lcache)
      (Thread/sleep 10)
      (is (= 1 @reloads))
      (is (= 42 (loading-cache/get lcache :key))))
    (testing "time to refresh again, but reload fails"
      (reset! reload-fails? true)
      (swap! db assoc :key 43)
      (.advance ticker 10 TimeUnit/SECONDS)
      (is (= 42 (loading-cache/get lcache :key)))
      (is (= 1 @reloads))
      (is (= 42 (loading-cache/get lcache :key)))
      (is (= 1 @reloads))
      (reset! reload-fails? false)
      (.advance ticker 10 TimeUnit/SECONDS)
      (is (= 42 (loading-cache/get lcache :key)))
      (loading-cache/cleanup lcache)
      (Thread/sleep 10)
      (is (= 2 @reloads))
      (is (= 43 (loading-cache/get lcache :key)))
      (is (= 2 @reloads)))))

(deftest async-auto-async-refresh
  (let [loads (atom 0)
        reloads (atom 0)
        reload-fails? (atom false)
        db (atom {:key 17})
        acl (common/reify-async-cache-loader
              (fn [k _executor]
                  (swap! loads inc)
                  (p/resolved (get @db k)))
              (fn [k _old-v _executor]
                (swap! reloads inc)
                (if @reload-fails?
                  (p/resolved (ex-info "error" {}))
                  (p/resolved (get @db k)))))
        ticker (FakeTicker.)
        alcache (async-loading-cache/make-cache-async-loader acl {:refreshAfterWrite 10 
                                                                  :timeUnit :s
                                                                  :ticker (reify-ticker ticker)})]
    (testing "no key, load succeeds"
      (is (= 17 @(async-loading-cache/get alcache :key)))
      (is (= 1 @loads))
      (is (= 0 @reloads)))
    (testing "key exists, just return from cache"
      (is (= 17 @(async-loading-cache/get alcache :key)))
      (is (= 1 @loads))
      (is (= 0 @reloads)))
    (testing "key needs to be refreshed"
      (swap! db assoc :key 42)
      (.advance ticker 11 TimeUnit/SECONDS)
      (is (= 17 @(async-loading-cache/get alcache :key)))
      (is (= 1 @loads))
      (is (= 1 @reloads))
      (is (= 42 @(async-loading-cache/get alcache :key))))
    (testing "time to refresh again but relaod fails"
      (reset! reload-fails? true)
      (swap! db assoc :key 43)
      (.advance ticker 10 TimeUnit/SECONDS)
      (is (= 42 @(async-loading-cache/get alcache :key)))
      (is (= 1 @reloads))
      (reset! reload-fails? false)
      (.advance ticker 10 TimeUnit/SECONDS)
      (is (= 42 @(async-loading-cache/get alcache :key)))
      (is (= 2 @reloads))
      (is (= 43 @(async-loading-cache/get alcache :key))))))
  
(deftest compute
  (let [c (cache/make-cache)
        k "key"
        remapper (fn [_k v]
                   (if (some? v)
                     (str v "bar")
                     "foo"))]
    (cache/put! c k "foo")
    (is (= "foobar" (cache/compute c k remapper)))
    (doall 
      (pmap (fn [_]
              (cache/compute c k remapper))
            (range 100)))
    (let [expected (s/join (concat ["foobar"] (repeat 100 "bar")))]
      (is (= expected (cache/get-if-present c k))))))
