package com.pheeeew.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.common.logging.RequestTiming.Measurement;
import com.pheeeew.common.logging.RequestTiming.Stage;
import com.pheeeew.emotion.application.command.EmotionPressService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestServiceTimingAspectTest {

    @ParameterizedTest
    @CsvSource({"SUCCESS,60", "BEGIN_FAILURE,10", "BODY_FAILURE,70", "COMMIT_FAILURE,100", "JOINED,20"})
    void 자동_프록시의_트랜잭션_경계와_실패를_서비스_시간에_반영한다(Scenario scenario, long expected) {
        // given: 서비스 비즈니스 로직이 아닌 advice 순서와 트랜잭션 경계만 검증한다.
        var nanoTime = new AtomicLong();
        var original = new IllegalStateException("transaction failure");
        var manager = new TestTransactionManager(nanoTime, scenario, original);
        var target = mock(EmotionPressService.class);
        when(target.press(null, Map.of())).thenAnswer(invocation -> {
            nanoTime.addAndGet(20);
            if (scenario == Scenario.BODY_FAILURE) {
                throw original;
            }
            return null;
        });
        var request = new MockHttpServletRequest();
        Object previous = RequestTiming.begin(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Config.class);
            context.registerBean(EmotionPressService.class, () -> target);
            context.registerBean("transactionManager", TestTransactionManager.class, () -> manager);
            context.registerBean(RequestServiceTimingAspect.class, () -> new RequestServiceTimingAspect(nanoTime::get));
            context.refresh();
            var service = context.getBean(EmotionPressService.class);

            // when
            if (scenario == Scenario.JOINED) {
                new TransactionTemplate(manager).execute(status -> service.press(null, Map.of()));
            } else if (scenario == Scenario.SUCCESS) {
                assertThat(service.press(null, Map.of())).isNull();
            } else {
                assertThatThrownBy(() -> service.press(null, Map.of())).isSameAs(original);
            }

            // then: begin=10, body=20, commit=30, rollback=40. 일반 예외의 커밋 실패에는 롤백도 포함한다.
            assertThat(RequestTiming.snapshot(request)).containsExactlyEntriesOf(
                    Map.of(Stage.PRESS_SERVICE, Measurement.of(1, expected, expected)));
            if (scenario == Scenario.BEGIN_FAILURE) {
                verify(target, never()).press(null, Map.of());
            }
            if (scenario == Scenario.JOINED) {
                assertThat(nanoTime.get()).isEqualTo(60);
            }
        } finally {
            RequestTiming.restore(request, previous);
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    static class Config {
    }

    enum Scenario {
        SUCCESS, BEGIN_FAILURE, BODY_FAILURE, COMMIT_FAILURE, JOINED
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {

        private final AtomicLong nanoTime;
        private final Scenario scenario;
        private final IllegalStateException original;
        private boolean active;

        TestTransactionManager(AtomicLong nanoTime, Scenario scenario, IllegalStateException original) {
            this.nanoTime = nanoTime;
            this.scenario = scenario;
            this.original = original;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            nanoTime.addAndGet(10);
            if (scenario == Scenario.BEGIN_FAILURE) {
                throw original;
            }
            active = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            nanoTime.addAndGet(30);
            if (scenario == Scenario.COMMIT_FAILURE) {
                throw original;
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            nanoTime.addAndGet(40);
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active = false;
        }
    }
}
