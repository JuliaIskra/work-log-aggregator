# Work log aggregator

Parses work log and generates output.
In the output you can see how much time did you spend doing tasks during every day. Time is shown in absolute hours 
and in "focus factor hours". Focus factor hours are calculated based on the assumption that the work day is 8 hours.
Also you can see focus factor in percent for each day.
Example files are in `dev-resources`.

Sample output:

    2015-12-30
    FF = 91%
    total - 7 hours and 18 minutes (8 hours)
    profiling - 4 hours and 38 minutes (5 hours and 4 minutes)
    help Alena - 2 hours and 20 minutes (2 hours and 33 minutes)
    mail - 20 minutes (21 minutes)

## Usage

1. [Install leiningen](http://leiningen.org/#install).
2. Program takes 2 or 3 arguments:
 - input-filename -- file with work log to parse
 - mode -- how data will be aggregated: d -- by day, m -- by month (m is not supported yet)
 - count -- optional argument, how many last aggregated entries to show, if not given, shows all entries 
3. You can run the application from leiningen directly:


    lein run input-filename mode [count]

4. Or you can create a standalone jar and run it as a java application:


    lein uberjar
    java -jar target/uberjar/work_log_aggregator-0.3.0-standalone.jar input-filename mode [count]

## Examples

    lein run dev-resources/input.txt d
    lein run dev-resources/input.txt d 1
