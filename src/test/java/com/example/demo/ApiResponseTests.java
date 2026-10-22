package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;

import com.example.demo.dto.ApiResponse;
import com.example.demo.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

class ApiResponseTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void serializesResponseFieldsAndOmitsNullOptionalFields() throws Exception {
        ApiResponse<String> response = new ApiResponse<>(Instant.parse("2026-01-01T00:00:00Z"),
                200, "OK", "Created", "/api/demo", "safe-data", null);

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertTrue(json.contains("\"status\":200"));
        assertTrue(json.contains("\"data\":\"safe-data\""));
        assertFalse(json.contains("validationErrors"));
    }

    @Test
    void integrityFailureReturnsSafeConflictResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payment-methods");

        var response = handler.handleDataIntegrity(
                new org.springframework.dao.DataIntegrityViolationException("sensitive database detail"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("The request conflicts with existing database data.", response.getBody().message());
        assertEquals(Map.of(), response.getBody().validationErrors());
        assertFalse(response.getBody().message().contains("sensitive"));
    }

    @Test
    void unexpectedFailureDoesNotExposeExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo");

        var response = handler.handleUnexpectedException(
                new IllegalStateException("internal SQL and credential details"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected server error occurred.", response.getBody().message());
        assertFalse(response.getBody().message().contains("SQL"));
        assertFalse(response.getBody().message().contains("credential"));
    }
}
