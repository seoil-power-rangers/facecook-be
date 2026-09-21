package com.facecook.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 여러 작업을 같은 순간에 시작시키고 모든 결과와 예외를 회수하는 동시성 테스트용 도구.
 *
 * <p>전제조건: 각 작업은 자기 스레드에서 실행되므로 서비스 호출마다 별도 연결·트랜잭션을 쓴다.
 * 작업 사이의 순서를 더 세밀하게 제어해야 하면 작업 안에서 별도의 래치를 쓴다.</p>
 *
 * <p>부작용: 호출마다 스레드 풀을 만들고 반환 전에 종료한다. 제한 시간 안에 끝나지 않은 작업은
 * 인터럽트하고 테스트를 실패시킨다. 예외를 삼키지 않고 {@link Outcome}에 담아 돌려준다.</p>
 */
public final class ConcurrentRunner {

    private ConcurrentRunner() {
    }

    /**
     * 모든 작업이 준비된 뒤 동시에 출발시키고, 제한 시간 안에 끝난 결과를 입력 순서대로 돌려준다.
     *
     * @throws AssertionError 제한 시간 안에 끝나지 않은 작업이 있을 때
     */
    public static <T> List<Outcome<T>> runTogether(List<Callable<T>> tasks, Duration timeout) {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            await(ready, timeout);
            start.countDown();

            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                outcomes.add(collect(future, timeout));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch, Duration timeout) {
        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("작업이 제한 시간 안에 준비되지 않았다: " + timeout);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("대기 중 인터럽트되었다", exception);
        }
    }

    private static <T> Outcome<T> collect(Future<T> future, Duration timeout) {
        try {
            return new Outcome<>(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS), null);
        } catch (ExecutionException exception) {
            return new Outcome<>(null, exception.getCause());
        } catch (TimeoutException exception) {
            throw new AssertionError("작업이 제한 시간 안에 끝나지 않았다(교착 가능성): " + timeout, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("결과를 기다리는 중 인터럽트되었다", exception);
        }
    }

    /** 작업 하나의 결과. 성공하면 {@code value}, 예외로 끝나면 {@code error}가 채워진다. */
    public record Outcome<T>(T value, Throwable error) {

        public boolean succeeded() {
            return error == null;
        }
    }
}
