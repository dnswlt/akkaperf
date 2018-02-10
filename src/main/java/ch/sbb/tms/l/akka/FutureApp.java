package ch.sbb.tms.l.akka;

import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FutureApp {

    public static void main(String[] args) throws Exception {
        new FutureApp().run(args);
    }

    private void run(String[] args) throws Exception {
        final int numWorkers = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        final int numRuns = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        final int warmupRuns = Math.max(1000, numRuns / 100);
        System.out.printf("Using %d workers, %d warmup runs and %d benchmark runs\n", numWorkers, warmupRuns, numRuns);

        ExecutorService pool = new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        try {
            long started = 0;
            double total = 0;
            Random rnd = new Random();
            for (int i = 0; i < numRuns + warmupRuns; i++) {
                List<Future<Result>> futures = IntStream.range(0, numWorkers)
                        .mapToObj(j -> pool.submit(new Worker(rnd.nextDouble() * 1e6)))
                        .collect(Collectors.toList());
                total += futures.stream().mapToDouble(f -> get(f).y).sum();
                if (i == warmupRuns) {
                    started = System.nanoTime();
                }
            }
            final long duration = System.nanoTime() - started;
            System.out.println("Total sum: " + total);
            System.out.printf("Duration (ms): %.3f\n", (duration / 1e6));
            System.out.printf("Messages/s: %.3f\n", (numRuns * (2 + numWorkers * 2)) / (duration / 1e9));
            System.out.printf("Rounds/s: %.3f\n", (numRuns / (duration / 1e9)));
        }
        finally {
            pool.shutdown();
        }
    }

    private Result get(Future<Result> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class Result {
        private double y;

        public Result(double y) {
            this.y = y;
        }
    }

    static class Worker implements Callable<Result> {

        private final double x;

        public Worker(double x) {
            this.x = x;
        }

        @Override
        public Result call() throws Exception {
            return new Result(Math.sqrt(x));
        }
    }

}
