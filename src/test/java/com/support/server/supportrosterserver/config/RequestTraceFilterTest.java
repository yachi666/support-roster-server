package com.support.server.supportrosterserver.config;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class RequestTraceFilterTest {

    private MockMvc mockMvc;
    private AnnotationConfigWebApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.register(TestWebConfig.class);
        applicationContext.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(applicationContext.getBean(RequestTraceFilter.class))
            .build();
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clearAll();
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    void shouldGenerateTraceIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/test/trace"))
            .andExpect(status().isOk())
            .andExpect(header().exists(RequestTraceFilter.TRACE_ID_HEADER))
            .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, not(emptyOrNullString())))
            .andExpect(content().string(not(emptyOrNullString())));
    }

    @Test
    void shouldReuseIncomingTraceId() throws Exception {
        mockMvc.perform(get("/api/test/trace")
                .header(RequestTraceFilter.TRACE_ID_HEADER, "trace-from-client"))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, "trace-from-client"))
            .andExpect(content().string("trace-from-client"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/trace")
        String trace(@RequestHeader(value = RequestTraceFilter.TRACE_ID_HEADER, required = false) String traceIdHeader) {
            String traceId = ThreadContext.get("traceId");
            return traceIdHeader == null || traceIdHeader.isBlank() ? traceId : traceId;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(RequestTraceFilter.class)
    static class TestWebConfig {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }
}
