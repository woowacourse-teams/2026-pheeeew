package com.pheeeew.emotion.presentation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pheeeew.common.config.MessageConverterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class MapResponseTimingConfigurationTest {

    @Test
    void 기존_MVC_JSON_응답과_협상을_유지하고_지도_응답만_계측한다() throws Exception {
        // given
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfiguration.class, MessageConverterConfig.class, MapResponseTimingConfiguration.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            MeterRegistry meters = context.getBean(MeterRegistry.class);

            // when / then
            mvc.perform(get("/api/v2/emotions/map").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(content().json("{\"items\":[{\"state\":\"한숨\"}],\"hasNext\":false}"));
            mvc.perform(get("/api/v2/emotions/map/regions").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andExpect(content().json("{\"items\":[{\"state\":\"한숨\"}],\"hasNext\":false}"));
            mvc.perform(get("/other").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            mvc.perform(get("/api/v2/emotions/map").accept(MediaType.APPLICATION_XML))
                    .andExpect(status().isNotAcceptable());

            assertThat(meters.get("pheeeew.map.response.write").tags("route", "pins", "outcome", "success")
                    .timer().count()).isEqualTo(1);
            assertThat(meters.get("pheeeew.map.response.write").tags("route", "regions", "outcome", "success")
                    .timer().count()).isEqualTo(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestConfiguration {
        @Bean
        MeterRegistry meters() {
            return new SimpleMeterRegistry();
        }

        @Bean
        TestController controller() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {
        @GetMapping({"/api/v2/emotions/map", "/api/v2/emotions/map/regions", "/other"})
        Map<String, Object> map() {
            return Map.of("items", List.of(Map.of("state", "한숨")), "hasNext", false);
        }
    }
}
