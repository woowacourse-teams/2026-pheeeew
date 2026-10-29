package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.query.EmotionMapQueryService;
import com.pheeeew.emotion.presentation.EmotionController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class MapReadControllerTimingTest {

    @Test
    void 컨트롤러_프록시가_지도_경로와_응답을_유지하고_중첩_서비스를_각각_한번_계측한다() throws Exception {
        // given: 조회 로직이 아니라 MVC 매핑과 AOP 적용 경계를 검증한다.
        var registry = new SimpleMeterRegistry();
        var aspect = new MapReadTimingAspect(new MapReadMetrics(registry));
        var target = mock(EmotionMapQueryService.class);
        when(target.findContentMapWithinBounds(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(EmotionMapPageView.of(List.of(), false, null));
        when(target.findRegionMap(any(), any(), any(), any())).thenReturn(List.of());
        var serviceFactory = new AspectJProxyFactory(target);
        serviceFactory.setProxyTargetClass(true);
        serviceFactory.addAspect(aspect);
        var controller = new EmotionController(null, null, serviceFactory.getProxy());
        var controllerFactory = new AspectJProxyFactory(controller);
        controllerFactory.setProxyTargetClass(true);
        controllerFactory.addAspect(aspect);
        var mvc = MockMvcBuilders.standaloneSetup((EmotionController) controllerFactory.getProxy())
                .setCustomArgumentResolvers(new DeviceResolver()).build();

        // when / then
        mvc.perform(get("/api/v2/emotions/map").param("minLongitude", "126").param("minLatitude", "37")
                        .param("maxLongitude", "128").param("maxLatitude", "39"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/api/v2/emotions/map/regions").param("minLongitude", "126").param("minLatitude", "37")
                        .param("maxLongitude", "128").param("maxLatitude", "39").param("level", "SIDO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        for (String stage : List.of("map_pins_controller", "map_pins_service", "map_regions_controller", "map_regions_service")) {
            assertThat(registry.get("pheeeew.map.read.stage").tags("stage", stage, "outcome", "success")
                    .timer().count()).isEqualTo(1);
        }
        registry.close();
    }

    private static class DeviceResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == UUID.class;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, WebDataBinderFactory binder) {
            return new UUID(0, 1);
        }
    }
}
