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
  (.toDateMidnight (entry :start-datetime)))

(defn group-by-task
  [entries]
  (group-by :task entries))

(defn map-second
  "Maps second element in a sequence of pairs"
  [f coll]
  (map (fn [[first second]] [first (f second)]) coll))

(defn aggregate-by-date-task
  [entries]
  (->> entries
      (group-by get-entry-day)
      (map-second group-by-task)
      (sort-by first)))

(defn -main
  [filename & args]
  (-> filename read-entries aggregate-by-date-task pprint))
