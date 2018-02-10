# Akka performance test

A small application that tests performance of Akka message throughput in a setting
where a single `Coordinator` actor sends messages to his child `Worker` actors,
that will perform some trivial computation and respond with a `Result` message.
Once all `Result`s have been gathered, the `Coordinator` itself responds with an
aggregated result.

## Build and run

    mvn clean install
    ./run.sh <num_workers> <num_runs>

## Sample output

    ./run.sh 1000 10000
    Using 1000 workers, 1000 warmup runs and 10000 benchmark runs
    Total sum: 7.332773590841454E9
    Duration (ms): 4135.350
    Messages/s: 4841186.137
    Rounds/s: 2418.175
    
Note: the `run.sh` uses Windows `-cp` syntax internally, it was written to be used in a Git Bash
or Cmder. Adapt accordingly when on Linux.
