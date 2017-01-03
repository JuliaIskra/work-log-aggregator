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


(defn get-entry-day
  [^Entry entry]
  (f/unparse (f/formatters :date) (.toDateMidnight (:start-datetime entry))))

(defn get-entry-month
  [^Entry entry]
  (f/unparse (f/formatters :year-month) (:start-datetime entry)))

(defn calculate-entry-duration
  [^Entry entry]
  (.toDuration (t/interval (:start-datetime entry) (:end-datetime entry))))

(defn group-by-task
  [entries]
  (group-by :task entries))

(defn sum-durations
  [durations]
  (reduce #(.plus %1 %2) durations))

(defn calculate-entries-total-duration
  "Calculates total duration of all entries in passed sequence"
  [entries]
  (sum-durations (map calculate-entry-duration entries)))

; todo try to merge functions aggregate-by-*-task
(defn aggregate-by-date-task
  [entries]
  (->> entries
       (group-by get-entry-day)
       (map (fn [[day entries]]
              (let [task-duration-pairs            (->> entries
                                                        ; returns sequence of (task entries)
                                                        group-by-task
                                                        ; returns sequence of (task duration)
                                                        (map-second calculate-entries-total-duration)
                                                        ; sort by duration in reverse order
                                                        (sort-by second #(compare %2 %1)))
                    total-time                     (sum-durations (map second task-duration-pairs))
                    task-duration-pairs-with-total (cons ["total" total-time] task-duration-pairs)]
                [day task-duration-pairs-with-total])))
       (sort-by first)))

(defn calculate-percents
  [total-time [task duration]]
  [task duration (/ (.getStandardMinutes duration)
                    (.getStandardMinutes total-time))])

(defn aggregate-by-month-task
  [entries]
  (->> entries
       (group-by get-entry-month)
       (map (fn [[month entries]]
              (let [task-duration-pairs            (->> entries
                                                        ; returns sequence of (task entries)
                                                        group-by-task
                                                        ; returns sequence of (task duration)
                                                        (map-second calculate-entries-total-duration)
                                                        ; sort by duration in reverse order
                                                        (sort-by second #(compare %2 %1)))
                    total-time                     (sum-durations (map second task-duration-pairs))
                    task-duration-pairs-with-total (->> task-duration-pairs
                                                        (cons ["total" total-time])
                                                        (map (partial calculate-percents total-time)))]
                [month task-duration-pairs-with-total])))
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


(defn format-task-duration
  [[task ^Duration duration]]
  (str task " - " (.print formatter (.toPeriod duration))))

(defn format-task-duration-with-percents
  [[task ^Duration duration percents]]
  (str (int (* percents 100)) "% - " task " (" (.print formatter (.toPeriod duration)) ")"))

; todo try to merge these two functions
(defn format-day-tasks
  [aggregated-entries]
  (->> aggregated-entries
       (map
         (fn [[day entries]]
           (concat [day]
                   (map format-task-duration entries))))
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
