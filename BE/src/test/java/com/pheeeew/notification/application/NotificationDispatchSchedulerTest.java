package com.pheeeew.notification.application;

import static com.pheeeew.notification.domain.NotificationOutboxType.RANK_DROPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NotificationDispatchSchedulerTest {

    @Test
    void 발송_실행이_예외를_던져도_그_유형의_다음_주기에_다시_실행한다() throws Exception {
        // given
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        AtomicInteger 순위_실행_수 = new AtomicInteger();
        CountDownLatch 두번째_순위_실행 = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (순위_실행_수.incrementAndGet() == 1) {
                throw new IllegalStateException("DB 일시 장애");
            }
            두번째_순위_실행.countDown();
            return null;
        }).when(dispatcher).dispatch(RANK_DROPPED);
        NotificationDispatchScheduler scheduler = new NotificationDispatchScheduler(dispatcher, true);

        // when
        boolean 다시_실행;
        try {
            scheduler.onApplicationReady();
            다시_실행 = 두번째_순위_실행.await(15, TimeUnit.SECONDS);
        } finally {
            scheduler.stop();
        }

        // then
        assertThat(다시_실행).isTrue();
    }

    @Test
    void 자동_시작을_끄면_애플리케이션_준비_뒤에도_발송을_실행하지_않는다() throws Exception {
        // given
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        CountDownLatch 발송_실행 = new CountDownLatch(1);
        doAnswer(invocation -> {
            발송_실행.countDown();
            return null;
        }).when(dispatcher).dispatch(any());
        NotificationDispatchScheduler scheduler = new NotificationDispatchScheduler(dispatcher, false);

        // when
        boolean 실행됨;
        try {
            scheduler.onApplicationReady();
            실행됨 = 발송_실행.await(1, TimeUnit.SECONDS);
        } finally {
            scheduler.stop();
        }

        // then
        assertThat(실행됨).isFalse();
    }
}
