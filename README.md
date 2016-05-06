# Work log aggregator

Parses work log and generates output where you can see how much time did you
spend doing tasks in a day in absolute hours and how much time did you spend
doing them taking into account focus factor (the program calculates focus factor
assuming that a work day is 8 hours). Example files are in `dev-resources`.

Sample output:

    2015-12-30
    total - 7 hours and 18 minutes (8 hours)
    profiling - 4 hours and 38 minutes (5 hours and 4 minutes)
    help Alena - 2 hours and 20 minutes (2 hours and 33 minutes)
    mail - 20 minutes (21 minutes)

## Usage

1. [Install leiningen](http://leiningen.org/#install).
2. You can run the application from leiningen directly:


    lein run \<input-filename\>

3. Or you can create a standalone jar and run it as a java application:


    lein uberjar
    java -jar target/uberjar/work_log_aggregator-0.3.0-standalone.jar \<input-filename\>

## Examples

    lein run dev-resources/input.txt
