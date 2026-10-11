package com.pheeeew.common.logging;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.LongSupplier;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public final class RequestTimingDataSource extends DelegatingDataSource {

    private final LongSupplier nanoTime;

    public RequestTimingDataSource(DataSource targetDataSource) {
        this(targetDataSource, System::nanoTime);
    }

    RequestTimingDataSource(DataSource targetDataSource, LongSupplier nanoTime) {
        super(targetDataSource);
        this.nanoTime = nanoTime;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return acquire(super::getConnection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return acquire(() -> super.getConnection(username, password));
    }

    private Connection acquire(ConnectionSupplier supplier) throws SQLException {
        long startedAt;
        try {
            startedAt = nanoTime.getAsLong();
        } catch (RuntimeException ignored) {
            // 시각을 읽지 못해도 원래 연결 획득은 한 번만 수행한다.
            return supplier.get();
        }

        boolean acquired = false;
        try {
            Connection connection = supplier.get();
            acquired = true;
            return connection;
        } finally {
            try {
                // 풀 대기 외에 내부 검증 등도 포함한 획득 호출 시간이며, 연결 점유 시간은 아니다.
                RequestTiming.record(acquired ? DataSourceTimingStage.DB_CONNECTION_ACQUIRE_SUCCESS : DataSourceTimingStage.DB_CONNECTION_ACQUIRE_FAILED,
                        nanoTime.getAsLong() - startedAt);
            } catch (RuntimeException ignored) {
                // 계측 실패가 원래 연결이나 획득 예외를 바꾸지 않게 한다.
            }
        }
    }

    @FunctionalInterface
    private interface ConnectionSupplier {

        Connection get() throws SQLException;
    }
}
