package com.pheeeew.pressrace.presentation;

import static com.pheeeew.pressrace.fixture.PressRankingFixture.NOW;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.OBSERVER_ID;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.순위;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.참가자;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.pressrace.application.PressRankingPublisher.Snapshot;
import com.pheeeew.pressrace.domain.PressView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PressSnapshotSenderTest {

    @Test
    void 보낸_A_뒤_대기중_B가_최신_A로_바뀌면_B와_중복_A를_보내지_않는다() {
        // given
        var executor = new QueuedExecutor();
        List<Snapshot> sent = new ArrayList<>();
        var sender = new PressSnapshotSender(new Object(), executor, sent::add, failure -> {});
        sender.offer(snapshot(10, PressView.PRESS, "1", true));
        executor.runNext();

        // when
        sender.offer(snapshot(20, PressView.PRESS, "2", false));
        sender.offer(snapshot(10, PressView.PRESS, "3", false));
        executor.runNext();

        // then
        assertThat(sent).extracting(Snapshot::snapshotVersion).containsExactly("1");
        assertThat(executor.size()).isZero();
    }

    @Test
    void 번호와_생성시각만_다르면_동일한_화면을_재송신하지_않는다() {
        // given
        var executor = new QueuedExecutor();
        List<Snapshot> sent = new ArrayList<>();
        var sender = new PressSnapshotSender(new Object(), executor, sent::add, failure -> {});
        Snapshot first = snapshot(10, PressView.PRESS, "1", true);
        sender.offer(first);
        executor.runNext();

        // when
        sender.offer(Snapshot.of(first.screen(), "2", NOW.plusMillis(250), false));
        executor.runNext();

        // then
        assertThat(sent).containsExactly(first);
    }

    @Test
    void 기준_재설정은_같은_화면도_더_큰_버전으로_보내고_화면_전환_기준도_보존한다() {
        // given
        var executor = new QueuedExecutor();
        List<Snapshot> sent = new ArrayList<>();
        var sender = new PressSnapshotSender(new Object(), executor, sent::add, failure -> {});
        sender.offer(snapshot(10, PressView.PRESS, "1", true));
        executor.runNext();

        // when
        sender.reset();
        sender.offer(snapshot(10, PressView.PRESS, "2", false));
        executor.runNext();
        sender.reset();
        sender.offer(snapshot(10, PressView.ALL, "3", true));
        executor.runNext();

        // then
        assertThat(sent).extracting(Snapshot::snapshotVersion).containsExactly("1", "2", "3");
        assertThat(sent).extracting(snapshot -> snapshot.screen().view())
                .containsExactly(PressView.PRESS, PressView.PRESS, PressView.ALL);
        assertThat(sent).allMatch(Snapshot::baseline);
    }

    @Test
    void 꺼낸_과거_화면이_송신_잠금을_기다리는_동안_전환하면_과거_화면은_폐기한다() throws Exception {
        // given
        Object sendMonitor = new Object();
        var executor = new QueuedExecutor();
        List<Snapshot> sent = new ArrayList<>();
        var sender = new PressSnapshotSender(sendMonitor, executor, sent::add, failure -> {});
        sender.offer(snapshot(10, PressView.PRESS, "1", true));
        Thread worker = Thread.ofPlatform().daemon().unstarted(executor.takeNext());

        // when
        synchronized (sendMonitor) {
            worker.start();
            송신_잠금_대기를_확인한다(worker);
            sender.reset();
            sender.offer(snapshot(10, PressView.ALL, "2", true));
        }
        worker.join(5_000);

        // then
        assertThat(worker.isAlive()).isFalse();
        assertThat(sent).extracting(Snapshot::snapshotVersion).containsExactly("2");
        assertThat(sent.getFirst().screen().view()).isEqualTo(PressView.ALL);
        assertThat(sent.getFirst().baseline()).isTrue();
    }

    @Test
    void 꺼낸_상태가_송신_잠금을_기다리는_동안_닫히면_아무것도_보내지_않는다() throws Exception {
        // given
        Object sendMonitor = new Object();
        var executor = new QueuedExecutor();
        List<Snapshot> sent = new ArrayList<>();
        var sender = new PressSnapshotSender(sendMonitor, executor, sent::add, failure -> {});
        sender.offer(snapshot(10, PressView.PRESS, "1", true));
        Thread worker = Thread.ofPlatform().daemon().unstarted(executor.takeNext());

        // when
        synchronized (sendMonitor) {
            worker.start();
            송신_잠금_대기를_확인한다(worker);
            sender.close();
        }
        worker.join(5_000);

        // then
        assertThat(worker.isAlive()).isFalse();
        assertThat(sent).isEmpty();
        sender.offer(snapshot(20, PressView.ALL, "2", true));
        assertThat(executor.size()).isZero();
    }

    @Test
    void 최초_기준이_송신중일_때_들어온_다음_변경을_중복_기준으로_보내지_않는다() throws Exception {
        // given
        var executor = new QueuedExecutor();
        List<Snapshot> sent = new ArrayList<>();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        var sender = new PressSnapshotSender(new Object(), executor, snapshot -> {
            if (sent.isEmpty()) {
                sending.countDown();
                래치를_기다린다(release);
            }
            sent.add(snapshot);
        }, failure -> failures.incrementAndGet());
        sender.offer(snapshot(10, PressView.PRESS, "1", true));
        Thread worker = Thread.ofPlatform().daemon().unstarted(executor.takeNext());

        // when
        worker.start();
        try {
            assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
            sender.offer(snapshot(20, PressView.PRESS, "2", false));
        } finally {
            release.countDown();
        }
        worker.join(5_000);

        // then
        assertThat(worker.isAlive()).isFalse();
        assertThat(failures.get()).isZero();
        assertThat(sent).extracting(Snapshot::snapshotVersion).containsExactly("1", "2");
        assertThat(sent).extracting(Snapshot::baseline).containsExactly(true, false);
    }

    private Snapshot snapshot(long score, PressView view, String version, boolean baseline) {
        return Snapshot.of(순위(List.of(참가자(1, score))).forDevice(OBSERVER_ID, view), version, NOW, baseline);
    }

    private void 송신_잠금_대기를_확인한다(Thread worker) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (worker.getState() != Thread.State.BLOCKED && worker.isAlive() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        // 이 시점에는 sender 잠금을 잡은 다른 스레드가 없어 BLOCKED는 sendMonitor 대기다.
        assertThat(worker.getState()).isEqualTo(Thread.State.BLOCKED);
    }

    private void 래치를_기다린다(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("송신 해제 래치 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static final class QueuedExecutor implements Executor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            takeNext().run();
        }

        private Runnable takeNext() {
            return tasks.remove();
        }

        private int size() {
            return tasks.size();
        }
    }
}
