package weather.service;

import weather.model.Reading;
import weather.model.Sensor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CONCEPT: concurrency. Classic PRODUCER / CONSUMER.
 *
 *   producer  : ScheduledExecutorService task, samples every sensor each tick
 *   queue     : bounded BlockingQueue (back-pressure instead of unbounded growth)
 *   consumer  : a dedicated daemon Thread (Runnable) draining the queue into the station
 *
 * The original used a busy {@code while(true){sleep(1000); if(running) log();}} thread that
 * could never be stopped, touched Swing from a non-EDT thread, and mutated unsynchronised
 * collections. This replacement can be started, stopped, re-timed and shut down cleanly.
 */
public class Simulator implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Simulator.class.getName());

    /** Poison pill: unique sentinel object telling the consumer to exit. */
    private static final Reading POISON = new Reading("STOP", weather.model.SensorType.WIND, 0, Instant.EPOCH);

    private final WeatherStation station;
    private final BlockingQueue<Reading> queue = new ArrayBlockingQueue<>(2_000);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Random rnd;
    private final Thread consumer;

    private ScheduledExecutorService producer;      // guarded by "this"
    private volatile long intervalMillis = 1000;

    public Simulator(WeatherStation station) {
        this(station, new Random());
    }

    public Simulator(WeatherStation station, Random rnd) {
        this.station = station;
        this.rnd = rnd;
        this.consumer = new Thread(new Consumer(), "weather-consumer");
        this.consumer.setDaemon(true);              // never keeps the JVM alive
        this.consumer.start();
    }

    /** CONCEPT: named inner class (non-static) that uses the outer instance's fields. */
    private class Consumer implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Reading r = queue.take();       // blocks efficiently, no polling

                    if (r == POISON) {
                        break;
                    }

                    try {
                        station.record(r);
                    } catch (RuntimeException ex) {  // one bad listener must not kill the thread
                        LOG.log(Level.WARNING, "record failed for " + r, ex);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore flag, then exit
            }
        }
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        producer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "weather-producer");
            t.setDaemon(true);

            return t;
        });

        producer.scheduleAtFixedRate(this::safeTick, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        producer.shutdownNow();
        producer = null;
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void setIntervalMillis(long ms) {
        if (ms < 50) {
            throw new IllegalArgumentException("interval too small");
        }

        this.intervalMillis = ms;

        if (running.get()) {
            stop();
            start();
        }
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    /** An exception thrown inside a scheduled task silently cancels future runs — so catch everything. */
    private void safeTick() {
        try {
            for (Reading r : generateRound()) {
                if (!queue.offer(r)) LOG.fine("queue full, dropping " + r);   // back-pressure
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "tick failed", t);
        }
    }

    /** One synthetic reading per sensor. Public so tests can drive it without timers. */
    public List<Reading> generateRound() {
        int hour = LocalTime.now().getHour();
        List<Reading> out = new ArrayList<>();

        for (Sensor s : station.sensors()) {
            out.add(new Reading(s.id(), s.type(), s.sample(rnd, hour), Instant.now()));
        }

        return out;
    }

    /** Wait until everything queued so far has been recorded (used by tests / shutdown). */
    public void drain(long timeoutMillis) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        while (!queue.isEmpty() && System.nanoTime() < end) {
            Thread.sleep(5);
        }

        Thread.sleep(20);                            // let consumer finish the item it already took
    }

    @Override
    public void close() {
        stop();
        queue.clear();
        queue.offer(POISON);

        try {
            consumer.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
