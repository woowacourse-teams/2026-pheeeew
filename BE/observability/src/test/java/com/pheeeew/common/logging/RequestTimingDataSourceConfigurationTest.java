package com.pheeeew.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;

class RequestTimingDataSourceConfigurationTest {

    @Test
    void 기본_Hikari_빈만_감싸고_이미_감싼_객체는_다시_감싸지_않는다() {
        // given
        BeanPostProcessor processor = RequestTimingDataSourceConfiguration.requestTimingDataSourcePostProcessor();
        var hikari = mock(HikariDataSource.class);
        var otherSource = mock(DataSource.class);

        // when
        Object wrapped = processor.postProcessAfterInitialization(hikari, "dataSource");

        // then
        assertThat(wrapped).isInstanceOf(RequestTimingDataSource.class);
        assertThat(((RequestTimingDataSource) wrapped).getTargetDataSource()).isSameAs(hikari);
        assertThat(processor.postProcessAfterInitialization(wrapped, "dataSource")).isSameAs(wrapped);
        assertThat(processor.postProcessAfterInitialization(hikari, "otherDataSource")).isSameAs(hikari);
        assertThat(processor.postProcessAfterInitialization(otherSource, "dataSource")).isSameAs(otherSource);
        verifyNoInteractions(hikari, otherSource);
    }
}
