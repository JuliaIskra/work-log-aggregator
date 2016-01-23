(ns work_log_aggregator.core
  (:gen-class :main true)
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
  "Calculates total duration of all entries in passed sequence"
  [entries]
  (reduce #(.plus %1 %2) (map get-entry-duration entries)))

(defn total-sum
  "Adds total duration of all entries in the passed sequence as a new element
  of the sequence"
  [entries]
  (conj entries ["total" (reduce #(.plus %1 %2) (map second entries))]))

(defn aggregate-by-date-task
  [entries]
  (->> entries
       ; group Entries by day
       (group-by get-entry-day)
       ; group Entries by task for every day
       (map-second (fn [e]
                     (->> e
                          ; returns sequence of (task sequnce-of-entries)
                          group-by-task
                          ; sum hours for every task
                          ; after this we have sequence of (task duration)
                          (map-second sum-hours)
                          ; add total amount of time spent in the day
                          ; as another task
                          total-sum
                          ; sort by duration desc
                          (sort-by second #(compare %2 %1)))))
       ; sort by day
       (sort-by first)))


(defn parse-date
  [date-str]
  (f/parse (f/formatter "yyyy-MM-dd HH:mm") date-str))

(defn parse-entry
  [[start task end]]
  (map->Entry {:start-datetime (parse-date start)
               :end-datetime   (parse-date end)
               :task           (s/trim task)}))

(defn read-entries
  "Returns lazy sequence of Entries"
  [reader]
  (->> (line-seq reader)
       (map s/trim)
       ; separate entries with empty lines
       ; returns (("start-time" "task" "end-time")("")("start-time" "task" "end-time"))
       (partition-by s/blank?)
       ; remove all sequences with all empty lines, i.e. ("")
       (remove #(every? s/blank? %1))
       (map parse-entry)))


(defn format-task-duration
  [[task ^Duration duration]]
  (str
    task
    " - "
    (.print (PeriodFormat/getDefault) (.toPeriod duration))))

(defn format-aggregated-entries
  [aggregated-entries]
  (->> aggregated-entries
       (map
         (fn [[day entries]]
           (cons
             (f/unparse (f/formatters :date) day)
             (map format-task-duration entries))))
       (interpose "")
       flatten))

(defn print-seq
  [coll]
  (doseq [s coll]
    (println s)))

(defn -main
  [filename]
  (with-open [reader (clojure.java.io/reader filename)]
    (-> reader
        read-entries
        aggregate-by-date-task
        format-aggregated-entries
        print-seq)))
