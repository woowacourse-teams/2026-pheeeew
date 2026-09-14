package com.pheeeew.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.application.DeviceChallengeService;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.application.DeviceTokenService;
import com.pheeeew.device.presentation.DeviceController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

@WebMvcTest(DeviceController.class)
@Import(MessageConverterConfig.class)
class MessageConverterConfigTest {

    @Autowired
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @MockitoBean
    private DeviceChallengeService deviceChallengeService;

    @Test
    void 어떤_엔드포인트도_CBOR_본문을_받지_않는다() {
        // when
        List<HttpMessageConverter<?>> converters = requestMappingHandlerAdapter.getMessageConverters();

        // then
        assertThat(converters)
                .noneMatch(converter -> converter.getSupportedMediaTypes().contains(MediaType.APPLICATION_CBOR));
    }

    @Test
    void JSON_변환기는_그대로_남는다() {
        // when
        List<HttpMessageConverter<?>> converters = requestMappingHandlerAdapter.getMessageConverters();

        // then
        assertThat(converters)
                .anyMatch(converter -> converter.getSupportedMediaTypes().contains(MediaType.APPLICATION_JSON));
    }
}
