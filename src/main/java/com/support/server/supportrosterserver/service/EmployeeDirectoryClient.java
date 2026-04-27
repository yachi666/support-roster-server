package com.support.server.supportrosterserver.service;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.support.server.supportrosterserver.config.SupportEmployeeProperties;
import com.support.server.supportrosterserver.dto.employee.EmployeeDirectoryLookupResponse;
import com.support.server.supportrosterserver.exception.BadRequestException;

    @Component
    public class EmployeeDirectoryClient {

        private final RestClient restClient;

        public EmployeeDirectoryClient(SupportEmployeeProperties properties) {
            SSLContext sslContext;
            try {
                sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize employee lookup TLS settings.", ex);
        }

        this.restClient = RestClient.builder()
            .requestFactory(new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom()
                        .useSystemProperties()
                        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                                .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                                        .useSystemProperties()
                                        .setSslContext(sslContext)
                                        .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                        .useSystemProperties()
                                        .buildClassic())
                                .build())
                        .build()
            ))
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public EmployeeDirectoryLookupResponse getEmployee(String staffId) {
        try {
            EmployeeDirectoryLookupResponse response = restClient.get()
                .uri("/api/employees/{staffId}", staffId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (_request, responseSpec) -> {
                    if (responseSpec.getStatusCode().value() == 404) {
                        throw new BadRequestException("Employee not found for staff ID " + staffId + ".");
                    }
                    throw new BadRequestException("Employee lookup was rejected for staff ID " + staffId + ".");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (_request, _responseSpec) -> {
                    throw new IllegalStateException("Employee lookup service is unavailable.");
                })
                .body(EmployeeDirectoryLookupResponse.class);

            if (response == null) {
                throw new IllegalStateException("Employee lookup returned no data for staff ID " + staffId + ".");
            }

            return response;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Employee lookup service is unavailable.", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Employee lookup failed for staff ID " + staffId + ".", ex);
        }
    }
}
