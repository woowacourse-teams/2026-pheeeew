package com.pheeeew.common.logging;

public class ExceptionLogFormatter {

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
}
