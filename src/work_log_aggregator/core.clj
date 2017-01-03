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

(def modes
  {"d" :day
   "m" :month})


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

(defn calculate-percents
  [total-time [task duration]]
  [task duration (/ (.getStandardMinutes duration)
                    (.getStandardMinutes total-time))])

(defn group-by-period
  [mode entries]
  (cond (= mode :day) (group-by get-entry-day entries)
        (= mode :month) (group-by get-entry-month entries)))

(defn aggregate-by-period
  [mode entries]
  (->> entries
       (group-by-period mode)
       (map (fn [[period entries]]
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
                [period task-duration-pairs-with-total])))
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
  (str task " (" (.print formatter (.toPeriod duration)) ")"))

(defn format-task-duration-percents
  [[task ^Duration duration percents]]
  (str (int (* percents 100)) "% - " (format-task-duration [task duration])))

(defn format-records-for-period
  [mode records]
  (cond (= mode :day) (format-task-duration records)
        (= mode :month) (format-task-duration-percents records)))

(defn format-output-records
  [mode output-records]
  (->> output-records
       (map
         (fn [[period records]]
           (concat [period]
                   (map (partial format-records-for-period mode) records))))
       (interpose "")
       flatten))

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
  [filename input-mode & [count]]
  (let [mode (get modes input-mode)]
    (if (nil? mode) (throw (RuntimeException. (str ("Unknown mode: " input-mode)))))
    (with-open [reader (clojure.java.io/reader filename)]
      (->> reader
           read-entries
           (aggregate-by-period mode)
           (take-last-if-specified count)
           (format-output-records mode)
           print-seq))))
