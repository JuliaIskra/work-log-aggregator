# Work log aggregator

Parses work log and generates output in two modes.

In day mode you can see how much time did you spend doing tasks during every day.
In month mode you can see how much time (in hours and in percents) did you spend doing your tasks during every month.

Example files are in `dev-resources`.

## Sample output:

For day mode:

    2016-07-29
    total (6 h 27 m)
    move task in section today (4 h 20 m)
    stand-up (50 m)
    teams breakfast (40 m)
    mail (37 m)

For month mode:

    2016-07
    100% - total (9 h 59 m)
    43% - move task in section today (4 h 20 m)
    17% - support app fix (1 h 44 m)
    16% - teams breakfast (1 h 40 m)
    10% - stand-up (1 h 5 m)
    6% - mail (37 m)
    5% - today, timezones (33 m)

## Usage

1. [Install leiningen](http://leiningen.org/#install).
2. Program takes 2 or 3 arguments:
 - input-filename - file with work log to parse
 - mode - how data will be aggregated: d - by day, m - by month
 - count - optional argument, how many last aggregated entries to show, if not given, all entries are shown
3. You can run the application from leiningen directly:

        lein run input-filename mode [count]

4. Or you can create a standalone jar and run it as a java application:

        lein uberjar
        java -jar target/uberjar/work_log_aggregator-*-standalone.jar input-filename mode [count]

## Examples

    lein run dev-resources/input.txt d
    lein run dev-resources/input.txt d 1
    lein run dev-resources/input.txt m
