package ch.sbb.tms.l.akka;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static akka.actor.SupervisorStrategy.escalate;
import static akka.actor.SupervisorStrategy.stop;

public class AkkaApp {

    public static void main(String[] args) throws Exception {
        new AkkaApp().run(args);
    }

    private void run(String[] args) throws Exception {
        final int numWorkers = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        final int numRuns = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        final int warmupRuns = Math.max(1000, numRuns / 100);
        System.out.printf("Using %d workers, %d warmup runs and %d benchmark runs\n", numWorkers, warmupRuns, numRuns);
        ActorSystem system = ActorSystem.create("akka");
        try {
            ActorRef coordinatorActor = system.actorOf(Coordinator.props(numWorkers), "coordinator");

            Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
            long started = 0;
            double total = 0;
            for (int i = 0; i < numRuns + warmupRuns; i++) {
                CompletableFuture<Object> task = PatternsCS.ask(coordinatorActor, new Start(), timeout).toCompletableFuture();
                total += ((Result) task.get()).y;
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
            system.terminate();
        }
    }

    /** Actors */

    static class Coordinator extends AbstractActor {
        LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        private int numChildren;
        private List<ActorRef> workers = new ArrayList<>();
        private Set<ActorRef> waitingFor = new HashSet<>();
        private ActorRef requester;
        private double sumOfSqrt;
        private int workerSeq;

        static Props props(int numChildren) {
            return Props.create(Coordinator.class, numChildren);
        }

        public Coordinator(int numChildren) {
            this.numChildren = numChildren;
        }

        @Override
        public void preStart() throws Exception {
            IntStream.range(0, numChildren).forEach(i -> createWorker());
        }

        private void createWorker() {
            ActorRef worker = getContext().actorOf(Worker.props(), "worker-" + workerSeq++);
            getContext().watch(worker);
            workers.add(worker);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(Start.class, this::onStart)
                    .match(Result.class, this::onResult)
                    .match(Terminated.class, this::onTerminated)
                    .build();
        }

        @Override
        public SupervisorStrategy supervisorStrategy() {
            return new OneForOneStrategy(10, Duration.create(1, TimeUnit.MINUTES), DeciderBuilder
                .match(RuntimeException.class, e -> stop())
                .matchAny(o -> escalate()).build());
        }

        private void onTerminated(Terminated t) {
            log.info("Dead: {}", t.getActor());
            waitingFor.remove(t.getActor());
            workers.remove(t.getActor());
            if (waitingFor.isEmpty()) {
                sendResult(requester);
            }
            createWorker();
        }

        private void onStart(Start s) {
//            log.info("Starting");
            requester = getSender();
            sumOfSqrt = 0;
            Random rnd = new Random();
            workers.forEach(w -> {
                w.tell(new WorkItem(rnd.nextDouble() * 1e6), getSelf());
                waitingFor.add(w);
            });
        }

        private void onResult(Result r) {
//            log.info("Received result from {}", getSender());
            waitingFor.remove(getSender());
            sumOfSqrt += r.y;
            if (waitingFor.isEmpty()) {
                sendResult(requester);
            }
        }

        private void sendResult(ActorRef recipient) {
            log.info("Sending Result");
            recipient.tell(new Result(sumOfSqrt), getSelf());
        }

    }

    static class Worker extends AbstractActor {

        LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        static Props props() {
            return Props.create(Worker.class);
        }

        @Override
        public void preStart() throws Exception {
//            log.info("Creating new Worker");
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(WorkItem.class, this::onWorkItem)
                    .build();
        }

        private void onWorkItem(WorkItem item) {
//            log.info("Working on item {}", item.x);
            if (new Random().nextDouble() < 0.001) {
                throw new IllegalArgumentException("Failure");
            }
            getSender().tell(new Result(Math.sqrt(item.x)), getSelf());
        }
    }


    /** Messages */

    static class Start {}

    static class WorkItem {
        private double x;

        public WorkItem(double x) {
            this.x = x;
        }
    }

    static class Result {
        private double y;

        public Result(double y) {
            this.y = y;
        }
    }

}
