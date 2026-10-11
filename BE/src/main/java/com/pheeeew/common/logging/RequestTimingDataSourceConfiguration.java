package com.pheeeew.common.logging;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class RequestTimingDataSourceConfiguration {

    @Bean
    static TimingPostProcessor requestTimingDataSourcePostProcessor() {
        return new TimingPostProcessor();
    }

    private static class TimingPostProcessor implements BeanPostProcessor, Ordered {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (beanName.equals("dataSource") && bean instanceof HikariDataSource dataSource) {
                // 원본 풀의 종료는 Spring이 맡고, 노출되는 연결 획득 경계만 감싼다.
                return new RequestTimingDataSource(dataSource);
            }
            return bean;
        }

        @Override
        public int getOrder() {
            // Boot의 lazy 연결 프록시(order=0) 안쪽에서 실제 획득을 측정한다.
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
