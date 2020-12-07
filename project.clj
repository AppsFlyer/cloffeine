(defproject com.appsflyer/cloffeine "0.1.7" 
  :description "A warpper over https://github.com/ben-manes/caffeine"
  :url "https://github.com/AppsFlyer/cloffeine"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.github.ben-manes.caffeine/caffeine "2.8.7"]]
  :plugins [[lein-codox "0.10.7"]]
  :codox {:output-path "codox"
          :source-uri "http://github.com/AppsFlyer/cloffeine/blob/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[jonase/eastwood "0.3.5"]
                             [lein-cloverage "1.1.1"]
                             [lein-eftest "0.5.9"]
                             [lein-kibit "0.1.7"]
                             [com.jakemccrary/lein-test-refresh "0.24.1"]
                             [lein-cloverage "1.1.2"]
                             [lein-ancient "0.6.15"]]
                   :eftest {:multithread? false
                            :report eftest.report.junit/report
                            :report-to-file "target/junit.xml"}
                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [criterium "0.4.6"]
                                  [cheshire "5.10.0"]
                                  [com.taoensso/timbre "5.1.0"]
                                  [clj-kondo "RELEASE"]
                                  [funcool/promesa "6.0.0"]
                                  [com.google.guava/guava-testlib "28.1-jre"]] 
                   :aliases {"clj-kondo" ["run" "-m" "clj-kondo.main"]
                             "lint"      ["run" "-m" "clj-kondo.main" "--lint" "src" "test"]}
                   :global-vars {*warn-on-reflection* true}}})
