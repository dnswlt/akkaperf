# Akka performance test

A small application that tests performance of Akka message throughput in a setting
where a single `Coordinator` actor sends messages to his child `Worker` actors,
that will perform some trivial computation and respond with a `Result` message.
Once all `Result`s have been gathered, the `Coordinator` itself responds with an
aggregated result.

## Build and run

    mvn clean install
    ./run_akka.sh <num_workers> <num_runs>

Note: the shell script uses Windows `-cp` syntax internally, it was written to be used in a Git Bash
or Cmder. Adapt accordingly when on Linux.


## Sample output

    ./run_akka.sh 1000 100000
    Using 1000 workers, 1000 warmup runs and 100000 benchmark runs
    Total sum: 6.7333823625157715E10
    Duration (ms): 40616.204
    Messages/s: 4929067.246
    Rounds/s: 2462.072
    
    
## Comparison with java.util.concurrent.Future

Turns out a very simplistic implementation of the same pattern based on `Future`s is only a bit less than
twice as fast. You can run it with `run_future.sh` with the same arguments as for `run_akka.sh`.
I'm quite happy with Akka's performance!

We cannot judge by these raw numbers alone that Futures are "better", because the Future-based version just uses
throw-away `Callable`s which cannot do anything than perform a simple computation. In the Actor-based
you get a whole lot of infrastructure for free that you will need in more realistic scenarios. Worker actors
can retain internal state, communicate with other actors, fail and notify the Coordinator, etc. etc.

    ./run_future.sh 1000 100000
    Using 1000 workers, 1000 warmup runs and 100000 benchmark runs
    Total sum: 6.733093819716195E10
    Duration (ms): 25985.128
    Messages/s: 7704406.861
    Rounds/s: 3848.355
