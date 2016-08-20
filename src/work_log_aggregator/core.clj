(ns work_log_aggregator.core
  (:gen-class :main true)
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as s])
  (:import (org.joda.time.format PeriodFormat)
           (org.joda.time DateTime Duration)))


(defn map-second
  "Maps second element in a sequence of pairs"
  [f coll]
  (map (fn [[first second]] [first (f second)]) coll))


(defrecord Entry [^DateTime start-datetime
                  ^DateTime end-datetime
                  ^String task])

(defn entry-day
  [^Entry entry]
  (.toDateMidnight (:start-datetime entry)))

(defn entry-duration
  [entry]
  (.toDuration (t/interval (:start-datetime entry) (:end-datetime entry))))

(defn group-by-task
  [entries]
  (group-by :task entries))

(defn sum-hours
  "Calculates total duration of all entries in passed sequence"
  [entries]
  (reduce #(.plus %1 %2) (map entry-duration entries)))

(defn total-sum
  "Calculates total duration of all entries"
  [entries]
  (reduce #(.plus %1 %2) (map second entries)))

(defn calculate-ff
  [total-time]
  (let [day-duration (Duration/standardHours 8)]
    (/
      (.getStandardMinutes total-time)
      (.getStandardMinutes day-duration))))

(defn add-ff-duration
  [ff [task-name duration]]
  [task-name
   duration
   (Duration/standardMinutes (/ (.getStandardMinutes duration) ff))])

(defn aggregate-by-date-task
  [entries]
  (->> entries
       (group-by entry-day)
       (map (fn [[day entries]]
              (let [tasks          (->> entries
                                        ; returns sequence of (task sequnce-of-entries)
                                        group-by-task
                                        ; sum hours for every task
                                        ; after this we have sequence of (task duration)
                                        (map-second sum-hours)
                                        ; sort by duration in reverse order
                                        (sort-by second #(compare %2 %1)))
                    total-time     (total-sum tasks)
                    ff             (calculate-ff total-time)
                    tasks-ff-total (->> tasks
                                        (cons ["total" total-time])
                                        (map (partial add-ff-duration ff)))]
                [day ff tasks-ff-total])))
       ; sort by day
       (sort-by first)))

(defn aggregate-by-month-task
  [entries]
  )

(defn aggregate-by
  [mode entries]
  (cond (= mode "d") (aggregate-by-date-task entries)
        (= mode "m") (aggregate-by-month-task entries)
        :else (throw (RuntimeException. (str "Unknown mode: " mode)))))


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
  [[task ^Duration duration ^Duration ff-duration]]
  (str
    task
    " - "
    (.print (PeriodFormat/getDefault) (.toPeriod duration))
    " ("
    (.print (PeriodFormat/getDefault) (.toPeriod ff-duration))
    ")"))

(defn format-ff
  [ff]
  (str "FF = " (int (* 100 ff)) "%"))

(defn format-aggregated-entries
  [aggregated-entries]
  (->> aggregated-entries
       (map
         (fn [[day ff entries]]
           (concat [(f/unparse (f/formatters :date) day)
                    (format-ff ff)]
                   (map format-task-duration entries))))
       (interpose "")
       flatten))

(defn print-seq
  [coll]
  (doseq [s coll]
    (println s)))


(defn -main
  "Takes file with filename with input data to parse in mode (d -- days, m -- months)
  and returns data for the last count days/months"
  ([filename mode]
   (with-open [reader (clojure.java.io/reader filename)]
     (->> reader
          read-entries
          (aggregate-by mode)
          format-aggregated-entries
          print-seq)))
  ([filename mode count]
   (with-open [reader (clojure.java.io/reader filename)]
     (->> reader
          read-entries
          (aggregate-by mode)
          (take-last (read-string count))
          format-aggregated-entries
          print-seq))))
