package com.pheeeew.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExceptionLogFormatterTest {

    private static final StackTraceElement 서비스_프레임 = 프레임("com.pheeeew.groups.application.GroupService", "findMine", 84);
    private static final StackTraceElement 컨트롤러_프레임 = 프레임("com.pheeeew.groups.presentation.GroupController", "findMine", 41);
    private static final StackTraceElement 외부_프레임 = 프레임("java.lang.Enum", "valueOf", 293);

    private final ExceptionLogFormatter formatter = new ExceptionLogFormatter();

    @Test
    void 발생_위치는_가장_깊은_원인의_첫_우리_코드_프레임이다() {
        // given
        Throwable cause = 예외(null, 외부_프레임, 서비스_프레임, 컨트롤러_프레임);
        Throwable failure = 예외(cause, 컨트롤러_프레임);

        // when
        String origin = formatter.origin(failure);

        // then
        assertThat(origin).isEqualTo("GroupService.findMine:84");
    }

    @Test
    void 발생_위치는_프록시와_필터_프레임을_건너뛴다() {
        // given
        Throwable failure = 예외(null,
                프레임("com.pheeeew.groups.application.GroupService$$SpringCGLIB$$0", "findMine", -1),
                프레임("com.pheeeew.common.logging.RequestLoggingFilter", "doFilterInternal", 43),
                프레임("com.pheeeew.activity.infra.DeviceActivityFilter", "doFilter", 30),
                프레임("jdk.proxy2.$Proxy120", "findMine", -1),
                프레임("com.pheeeew.groups.application.GroupService$Inner", "lambda$findMine$0", 90));

        // when
        String origin = formatter.origin(failure);

        // then
        assertThat(origin).isEqualTo("GroupService$Inner.lambda$findMine$0:90");
    }

    @Test
    void 깊은_원인에_우리_코드_프레임이_없으면_얕은_쪽에서_찾는다() {
        // given
        Throwable failure = 예외(예외(null, 외부_프레임), 외부_프레임, 서비스_프레임);

        // when
        String origin = formatter.origin(failure);

        // then
        assertThat(origin).isEqualTo("GroupService.findMine:84");
    }

    @Test
    void 우리_코드_프레임이_없거나_다섯_단계_밖에만_있으면_발생_위치가_없다() {
        // given
        Throwable withoutOwnFrame = 예외(예외(null, 외부_프레임), 외부_프레임);
        Throwable ownFrameAtSixth = 사슬(5, 예외(null, 서비스_프레임));

        // when / then
        assertThat(formatter.origin(withoutOwnFrame)).isNull();
        assertThat(formatter.origin(ownFrameAtSixth)).isNull();
        assertThat(formatter.origin(null)).isNull();
    }

    @Test
    void 예외_종류는_다섯_단계_안에서_가장_깊은_원인의_단순_이름이다() {
        // given
        Throwable twoLevels = new IllegalStateException("top", new IllegalArgumentException("deep"));
        Throwable sixLevels = 사슬(4, new IllegalArgumentException("fifth", new SQLException("sixth", "23505")));

        // when / then
        assertThat(formatter.exceptionType(twoLevels)).isEqualTo("IllegalArgumentException");
        assertThat(formatter.exceptionType(sixLevels)).isEqualTo("IllegalArgumentException");
        assertThat(formatter.exceptionType(null)).isNull();
    }

    @Test
    void 순환하는_원인_사슬에서도_멈춘다() {
        // given
        Exception failure = new Exception("top");
        IllegalStateException cause = new IllegalStateException("cause", failure);
        failure.initCause(cause);
        failure.setStackTrace(new StackTraceElement[]{외부_프레임});
        cause.setStackTrace(new StackTraceElement[]{서비스_프레임});

        // when / then
        assertThat(formatter.exceptionType(failure)).isEqualTo("Exception");
        assertThat(formatter.origin(failure)).isEqualTo("GroupService.findMine:84");
        assertThat(formatter.sqlState(failure)).isNull();
        assertThat(formatter.messages(failure)).hasSize(5);
    }

    @Test
    void DB_원인이면_어떤_값도_예외_메시지를_읽지_않고_만든다() {
        // given
        Throwable failure = new MessageTrap(new MessageTrap(new SQLMessageTrap()));

        // when / then
        assertThat(formatter.format(failure)).contains("MessageTrap", "SQLMessageTrap");
        assertThat(formatter.exceptionType(failure)).isEqualTo("SQLMessageTrap");
        assertThat(formatter.origin(failure)).startsWith("ExceptionLogFormatterTest.");
        assertThat(formatter.sqlState(failure)).isEqualTo("23505");
        assertThat(formatter.messages(failure)).isEmpty();
    }

    @Test
    void DB_원인이_아니면_SQL_상태가_없다() {
        // given
        Throwable failure = 사슬(50, null);

        // when / then
        assertThat(formatter.sqlState(failure)).isNull();
        assertThat(formatter.sqlState(사슬(50, new SQLException("row", "23505")))).isNull();
        assertThat(formatter.sqlState(null)).isNull();
    }

    @Test
    void DB_원인이_아닌_예외의_메시지는_원인_순서대로_클래스_이름과_함께_남는다() {
        // given
        Throwable failure = new IllegalStateException("집계 상태가 초기화되지 않았습니다.",
                new IllegalArgumentException(null, new UnsupportedOperationException("deep")));

        // when
        List<String> messages = formatter.messages(failure);

        // then
        assertThat(messages).containsExactly(
                "java.lang.IllegalStateException: 집계 상태가 초기화되지 않았습니다.",
                "java.lang.IllegalArgumentException",
                "java.lang.UnsupportedOperationException: deep");
        assertThat(formatter.messages(null)).isEmpty();
    }

    @Test
    void 메시지는_줄바꿈을_공백으로_바꾸고_500자와_다섯_단계까지만_남긴다() {
        // given
        Throwable failure = new IllegalStateException("first\r\n   second\nthird" + "z".repeat(600),
                사슬(4, new IllegalStateException("sixth")));

        // when
        List<String> messages = formatter.messages(failure);

        // then
        String prefix = "java.lang.IllegalStateException: ";
        String firstLine = "first second third";
        assertThat(messages).containsExactly(
                prefix + firstLine + "z".repeat(500 - firstLine.length()) + "...",
                prefix + "wrapper-3", prefix + "wrapper-2", prefix + "wrapper-1", prefix + "wrapper-0");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("SQL_상태_사례")
    void SQL_상태는_가장_깊은_SQL_예외부터_형식에_맞는_첫_값이고_없으면_unknown이다(
            String description,
            Throwable failure,
            String expected
    ) {
        // given / when
        String sqlState = formatter.sqlState(failure);

        // then
        assertThat(sqlState).isEqualTo(expected);
    }

    private static Stream<Arguments> SQL_상태_사례() {
        return Stream.of(
                Arguments.of("최상위가 SQL 예외",
                        new SQLException("row", "23505"), "23505"),
                Arguments.of("감싼 예외 아래의 하위 클래스",
                        new IllegalStateException("w", new BatchUpdateException("row", "42P01", new int[0])), "42P01"),
                Arguments.of("SQL 예외가 둘이면 가장 깊은 것",
                        new SQLException("a", "08006", new SQLException("b", "23502")), "23502"),
                Arguments.of("가장 깊은 것의 상태가 형식에 안 맞으면 그 위의 것",
                        new SQLException("a", "08006", new SQLException("b", "row value")), "08006"),
                Arguments.of("일곱 번째 원인의 SQL 예외",
                        사슬(6, new SQLException("row", "23505")), "23505"),
                Arguments.of("상태가 null",
                        new SQLException("row", (String) null), "unknown"),
                Arguments.of("값이 섞인 상태",
                        new SQLException("row", "Key (memo)=(secret)"), "unknown")
        );
    }

    private static StackTraceElement 프레임(String className, String methodName, int line) {
        return new StackTraceElement(className, methodName, null, line);
    }

    private static Throwable 예외(Throwable cause, StackTraceElement... frames) {
        Throwable failure = new IllegalStateException("message", cause);
        failure.setStackTrace(frames);
        return failure;
    }

    private static Throwable 사슬(int wrappers, Throwable innermost) {
        Throwable failure = innermost;
        for (int index = 0; index < wrappers; index++) {
            failure = new IllegalStateException("wrapper-" + index, failure);
            failure.setStackTrace(new StackTraceElement[]{외부_프레임});
        }
        return failure;
    }

    private static class MessageTrap extends RuntimeException {

        MessageTrap(Throwable cause) {
            super("trap", cause);
        }

        @Override
        public String getMessage() {
            throw new AssertionError("DB 원인인데 예외 메시지를 읽었습니다");
        }
    }

    private static class SQLMessageTrap extends SQLException {

        SQLMessageTrap() {
            super("trap", "23505");
        }

        @Override
        public String getMessage() {
            throw new AssertionError("DB 원인인데 예외 메시지를 읽었습니다");
        }
    }
}
