package com.support.server.supportrosterserver.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.support.server.supportrosterserver.dto.ErrorResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void shouldReturnBadRequestForDuplicateHostnameConstraintViolation() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_workspace_linux_password_server_hostname\""
        );

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataIntegrityViolation(
            exception,
            new ServletWebRequest(new MockHttpServletRequest("POST", "/api/workspace/linux-passwords"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Hostname already exists.", response.getBody().getMessage());
        assertEquals("/api/workspace/linux-passwords", response.getBody().getPath());
    }
}
