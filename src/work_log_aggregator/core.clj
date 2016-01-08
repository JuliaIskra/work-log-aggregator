(ns work-log-aggregator.core
  (:gen-class)
  (:require [clj-time.core :as t])
  (:require [clj-time.format :as f])
  (:use clojure.pprint)
  (:import (org.joda.time.format PeriodFormat)))

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
  (.toDateMidnight (entry :start-datetime)))

(defn group-by-task
  [entries]
  (group-by :task entries))

(defn get-entry-interval
  [entry]
  (.toPeriod (t/interval (entry :start-datetime) (entry :end-datetime))))

(defn sum-hours
  [entries]
  (reduce #(.plus %1 %2) (map get-entry-interval entries)))

(defn map-second
  "Maps second element in a sequence of pairs"
  [f coll]
  (map (fn [[first second]] [first (f second)]) coll))

(defn aggregate-by-date-task
  [entries]
  (->> entries
       (group-by get-entry-day)
       (map-second #(->> %1 group-by-task (map-second sum-hours)))
       (sort-by first)))

(defn format-task-period
  [[task period]]
  (str
    task
    " - "
    (.print (PeriodFormat/getDefault) (.normalizedStandard period))))

(defn format-entries
  [entries]
  (clojure.string/join "\n" (map format-task-period entries)))

(defn format-aggregated-entries
  [aggregated-entries]
  (clojure.string/join "\n\n"
     (map
       (fn [[day entries]]
         (str
           (f/unparse (f/formatters :date) day)
           "\n"
           (format-entries entries)))
       aggregated-entries)))

(defn print-hours
  [aggregated-entries]
  (println (format-aggregated-entries aggregated-entries)))

(defn -main
  [filename & args]
  (-> filename read-entries aggregate-by-date-task print-hours))
