(ns work-log-aggregator.core
  (:gen-class)
  (:require [clj-time.core :as t])
  (:require [clj-time.format :as f])
  (:require [clojure.string :as s])
  (:import (org.joda.time.format PeriodFormat))
  (:import (org.joda.time DateTime Duration)))


(defn map-second
  "Maps second element in a sequence of pairs"
  [f coll]
  (map (fn [[first second]] [first (f second)]) coll))


(defrecord Entry [^DateTime start-datetime
                  ^DateTime end-datetime
                  ^String task])

(defn get-entry-day
  [^Entry entry]
  (.toDateMidnight (:start-datetime entry)))

(defn get-entry-duration
  [entry]
  (.toDuration (t/interval (:start-datetime entry) (:end-datetime entry))))

(defn group-by-task
  [entries]
  (group-by :task entries))

(defn sum-hours
  [entries]
  (reduce #(.plus %1 %2) (map get-entry-duration entries)))

(defn aggregate-by-date-task
  [entries]
  (->> entries
       (group-by get-entry-day)
       (map-second (fn [e]
                     (->> e
                          group-by-task
                          (map-second sum-hours)
                          (sort-by second #(compare %2 %1)))))
       (sort-by first)))


(defn parse-date
  [date-str]
  (f/parse (f/formatter "yyyy-MM-dd HH:mm") date-str))

(defn parse-entry
  [entry-str]
  (let [[start task end] (s/split entry-str #"[\r\n]+")]
    (map->Entry {:start-datetime (parse-date start)
                 :end-datetime   (parse-date end)
                 :task           (s/trim task)})))

(defn read-entries
  [filename]
  (map parse-entry (s/split (slurp filename) #"[\r\n]+ [\r\n]+")))


(defn format-task-duration
  [[task ^Duration duration]]
  (str
    task
    " - "
    (.print (PeriodFormat/getDefault) (.toPeriod duration))))

(defn format-aggregated-entries
  [aggregated-entries]
  (s/join
    "\n\n"
    (map
      (fn [[day entries]]
        (str
          (f/unparse (f/formatters :date) day)
          "\n"
          (s/join "\n" (map format-task-duration entries))))
      aggregated-entries)))


(defn -main
  [filename]
  (-> filename
      read-entries
      aggregate-by-date-task
      format-aggregated-entries
      println))
