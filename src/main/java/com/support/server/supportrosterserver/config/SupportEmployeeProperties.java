package com.support.server.supportrosterserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "support.employee")
public class SupportEmployeeProperties {

    @NotBlank
    private String baseUrl = "https://api.heet.uk";
}
