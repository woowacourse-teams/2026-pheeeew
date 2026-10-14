package com.pheeeew.common.logging;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.spi.LoggingEventBuilder;

public class ExceptionLogFormatter {

    private static final int MAX_CAUSES = 5;
    private static final int MAX_SQL_SEARCH_CAUSES = 50;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final String TRUNCATION_MARK = "...";
    private static final String UNKNOWN_SQL_STATE = "unknown";
    private static final Pattern SQL_STATE_SHAPE = Pattern.compile("^[0-9A-Z]{5}$");
    private static final Pattern LINE_BREAK = Pattern.compile("\\s*\\R\\s*");
    private static final String OWN_PACKAGE = "com.pheeeew.";
    private static final String PROXY_MARKER = "$$";
    private static final List<String> FILTER_METHODS = List.of("doFilter", "doFilterInternal");

    public String format(Throwable failure) {
        // 예외 메시지, suppressed 예외에는 SQL 값이나 요청 내용이 포함될 수 있다.
        StringBuilder stack = new StringBuilder();
        for (int cause = 0; failure != null && cause < 5; cause++, failure = failure.getCause()) {
            if (cause > 0) {
                stack.append("Caused by: ");
            }
            stack.append(failure.getClass().getName()).append('\n');
            StackTraceElement[] frames = failure.getStackTrace();
            for (int index = 0; index < Math.min(frames.length, 20); index++) {
                stack.append("  at ").append(frames[index]).append('\n');
            }
        }
        return stack.toString();
    }

    public void addFailureFields(LoggingEventBuilder event, Throwable failure) {
        String origin = origin(failure);
        String sqlState = sqlState(failure);
        List<String> messages = messages(failure);

        event.addKeyValue("exceptionType", exceptionType(failure));
        if (origin != null) {
            event.addKeyValue("origin", origin);
        }
        if (sqlState != null) {
            event.addKeyValue("sqlState", sqlState);
        }
        if (!messages.isEmpty()) {
            event.addKeyValue("errorMessages", messages);
        }
        event.addKeyValue("errorStack", format(failure));
    }

    public String exceptionType(Throwable failure) {
        List<Throwable> chain = findCauseChain(failure);
        if (chain.isEmpty()) {
            return null;
        }
        return chain.getLast().getClass().getSimpleName();
    }

    public String origin(Throwable failure) {
        List<Throwable> chain = findCauseChain(failure);
        for (int index = chain.size() - 1; index >= 0; index--) {
            for (StackTraceElement frame : chain.get(index).getStackTrace()) {
                if (isOwnFrame(frame)) {
                    return formatFrame(frame);
                }
            }
        }
        return null;
    }

    public String sqlState(Throwable failure) {
        List<SQLException> sqlFailures = new ArrayList<>();
        for (int depth = 0; failure != null && depth < MAX_SQL_SEARCH_CAUSES; depth++, failure = failure.getCause()) {
            if (failure instanceof SQLException sqlFailure) {
                sqlFailures.add(sqlFailure);
            }
        }
        if (sqlFailures.isEmpty()) {
            return null;
        }
        for (SQLException sqlFailure : sqlFailures.reversed()) {
            String sqlState = sqlFailure.getSQLState();
            if (sqlState != null && SQL_STATE_SHAPE.matcher(sqlState).matches()) {
                return sqlState;
            }
        }
        return UNKNOWN_SQL_STATE;
    }

    public List<String> messages(Throwable failure) {
        if (sqlState(failure) != null) {
            return List.of();
        }
        return findCauseChain(failure).stream()
                .map(this::describe)
                .toList();
    }

    private List<Throwable> findCauseChain(Throwable failure) {
        List<Throwable> chain = new ArrayList<>();
        for (int cause = 0; failure != null && cause < MAX_CAUSES; cause++, failure = failure.getCause()) {
            chain.add(failure);
        }
        return chain;
    }

    private boolean isOwnFrame(StackTraceElement frame) {
        String className = frame.getClassName();
        return className.startsWith(OWN_PACKAGE)
                && !className.contains(PROXY_MARKER)
                && !FILTER_METHODS.contains(frame.getMethodName());
    }

    private String formatFrame(StackTraceElement frame) {
        String className = frame.getClassName();
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return simpleName + "." + frame.getMethodName() + ":" + frame.getLineNumber();
    }

    private String describe(Throwable failure) {
        String message = failure.getMessage();
        if (message == null) {
            return failure.getClass().getName();
        }
        String singleLine = LINE_BREAK.matcher(message).replaceAll(" ");
        if (singleLine.length() > MAX_MESSAGE_LENGTH) {
            singleLine = singleLine.substring(0, MAX_MESSAGE_LENGTH) + TRUNCATION_MARK;
        }
        return failure.getClass().getName() + ": " + singleLine;
    }
}
