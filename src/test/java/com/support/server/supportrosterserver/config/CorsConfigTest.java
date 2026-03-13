package com.support.server.supportrosterserver.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.Filter;

class CorsConfigTest {

    private MockMvc mockMvc;

    private AnnotationConfigWebApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.register(TestWebConfig.class);
        applicationContext.refresh();

        FilterRegistrationBean<Filter> registration = applicationContext.getBean("privateNetworkFilter", FilterRegistrationBean.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(registration.getFilter())
            .build();
    }

    @AfterEach
    void tearDown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    void shouldAllowAnyFrontendOriginOnPreflightRequest() throws Exception {
        mockMvc.perform(options("/api/test")
                .header("Origin", "https://frontend.example.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.com"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
            .andExpect(header().string("Access-Control-Allow-Private-Network", "true"))
            .andExpect(header().string("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD"));
    }

    @Test
    void shouldAllowAnyFrontendOriginOnActualRequest() throws Exception {
        mockMvc.perform(get("/api/test")
                .header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
            .andExpect(header().string("Access-Control-Allow-Private-Network", "true"))
            .andExpect(content().string("ok"));
    }

    @Test
    void shouldHandlePrivateNetworkAccessRequest() throws Exception {
        mockMvc.perform(options("/api/test")
                .header("Origin", "http://hk120050033.hk.hsbc:8091")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Private-Network", "true"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://hk120050033.hk.hsbc:8091"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
            .andExpect(header().string("Access-Control-Allow-Private-Network", "true"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test")
        String test() {
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(CorsConfig.class)
    static class TestWebConfig {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }
}
