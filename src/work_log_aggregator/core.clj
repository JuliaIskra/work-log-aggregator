(ns work-log-aggregator.core
  (:gen-class)
  ;(:require '[clj-time.core :as t])
  (:require [clj-time.format :as f])
  (:use clojure.pprint))

(defn parse-date
  [date-str]
  (f/parse (f/formatter "yyyy-MM-dd HH:mm") date-str))

(defn parse-entry
  [entry-str]
  (let [lines (clojure.string/split entry-str #"[\r\n]+")]
    {:start-datetime (parse-date (get lines 0))
     :end-datetime   (parse-date (get lines 2))
     :task           (clojure.string/trim (get lines 1))}))

(defn read-entries
  [filename]
  (map parse-entry (clojure.string/split (slurp filename) #"[\r\n]+ [\r\n]+")))

(defn get-entry-day
  [entry]
  (.toDateMidnight (get entry :start-datetime)))

(defn aggregate-by-date-task
  [entries]
  (sort-by first (group-by get-entry-day entries)))

(defn -main
  [filename & args]
  (-> filename read-entries aggregate-by-date-task pprint))
