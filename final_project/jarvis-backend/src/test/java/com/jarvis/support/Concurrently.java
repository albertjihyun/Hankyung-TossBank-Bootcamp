package com.jarvis.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 같은 작업들을 "동시에" 출발시키는 도우미. 스레드를 만드는 순서대로 실행하면 사실상 순차 실행이 되어
 * 경합이 일어나지 않으므로, 전원이 준비될 때까지 문(latch)을 닫아 두었다가 한 번에 연다.
 */
public final class Concurrently {

    private Concurrently() {
    }

    /** 각 작업의 결과 또는 던진 예외를 순서대로 돌려준다 */
    public static <T> List<Outcome<T>> run(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome<>(task.call(), null);
                    } catch (Exception e) {
                        return new Outcome<T>(null, e);
                    }
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("작업 스레드가 준비되지 않았다");
            }
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                try {
                    outcomes.add(future.get(60, TimeUnit.SECONDS));
                } catch (Exception e) {
                    throw new IllegalStateException("작업이 제한 시간 안에 끝나지 않았다 — 데드락을 의심할 것", e);
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    public record Outcome<T>(T value, Exception error) {

        public boolean succeeded() {
            return error == null;
        }
    }
}
