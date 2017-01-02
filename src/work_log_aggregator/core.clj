(ns work_log_aggregator.core
  (:gen-class :main true)
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as s])
  (:import (org.joda.time.format PeriodFormatterBuilder PeriodFormatter)
           (org.joda.time DateTime Duration)))

(defn map-second
  "Maps second element in a sequence of pairs"
  [f coll]
  (map (fn [[first second]] [first (f second)]) coll))


(defrecord Entry [^DateTime start-datetime
                  ^DateTime end-datetime
                  ^String task])

(def ^PeriodFormatter formatter
  (-> (new PeriodFormatterBuilder)
      .appendHours
      (.appendSuffix " h")
      (.appendSeparator " ")
      .appendMinutes
      (.appendSuffix " m")
      .toFormatter))


(defn entry-day
  [^Entry entry]
  ; todo parse to string?
  (.toDateMidnight (:start-datetime entry)))

(defn entry-month
  [^Entry entry]
  (f/unparse (f/formatters :year-month) (:start-datetime entry)))

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

(defn total-duration
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

; todo try to merge functions aggregate-by-*-task
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
                    total-time     (total-duration tasks)
                    ff             (calculate-ff total-time)
                    tasks-ff-total (->> tasks
                                        (cons ["total" total-time])
                                        (map (partial add-ff-duration ff)))]
                [day ff tasks-ff-total])))
       ; sort by day
       (sort-by first)))

(defn calculate-percents
  [total-time [task-name duration]]
  [task-name duration (/ (.getStandardMinutes duration)
                         (.getStandardMinutes total-time))])

(defn aggregate-by-month-task
  [entries]
  (->> entries
       (group-by entry-month)
       (map (fn [[month entries]]
              (let [tasks       (->> entries
                                     ; returns sequence of (task sequnce-of-entries)
                                     group-by-task
                                     ; sum hours for every task
                                     ; after this we have sequence of (task duration)
                                     (map-second sum-hours)
                                     ; sort by duration in reverse order
                                     (sort-by second #(compare %2 %1)))
                    total-time  (total-duration tasks)
                    tasks-total (->> tasks
                                     (cons ["total" total-time])
                                     (map (partial calculate-percents total-time)))]
                [month tasks-total])))
       (sort-by first)))

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


(defn format-task-duration-with-ff
  [[task ^Duration duration ^Duration ff-duration]]
  (str task " - " (.print formatter (.toPeriod duration)) " (" (.print formatter (.toPeriod ff-duration)) ")"))

(defn format-task-duration-with-percents
  [[task ^Duration duration percents]]
  (str (int (* percents 100)) "% - " task " (" (.print formatter (.toPeriod duration)) ")"))

; todo remove ff
(defn format-ff
  [ff]
  (str "FF = " (int (* 100 ff)) "%"))
; todo try to merge these two functions
(defn format-day-tasks
  [aggregated-entries]
  (->> aggregated-entries
       (map
         (fn [[day ff entries]]
           (concat [(f/unparse (f/formatters :date) day)
                    (format-ff ff)]
                   (map format-task-duration-with-ff entries))))
       (interpose "")
       flatten))

(defn format-month-tasks
  [aggregated-entries]
  (->> aggregated-entries
       (map
         (fn [[month entries]]
           (concat [month]
                   (map format-task-duration-with-percents entries))))
       (interpose "")
       flatten))

; todo check mode once
(defn format-aggregated-entries
  [mode aggregated-entries]
  (cond (= mode "d") (format-day-tasks aggregated-entries)
        (= mode "m") (format-month-tasks aggregated-entries)
        :else (throw (RuntimeException. (str "Unknown mode: " mode)))))

(defn print-seq
  [coll]
  (doseq [s coll]
    (println s)))

(defn take-last-if-specified
  [count coll]
  (if count
    (take-last (read-string count) coll)
    coll))


(defn -main
  "Takes file with filename with input data to parse in mode (d -- days, m -- months)
  and returns data for the last count days/months"
  ([filename mode & [count]]
   (with-open [reader (clojure.java.io/reader filename)]
     (->> reader
          read-entries
          (aggregate-by mode)
          (take-last-if-specified count)
          (format-aggregated-entries mode)
          print-seq))))
