package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.exception.GlobalExceptionHandler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class GlobalExceptionHandlerMvcTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validRequestKeepsControllerSuccessContract() throws Exception {
        mockMvc.perform(post("/mvc-test")
                        .contentType("application/json")
                        .content("{\"name\":\"sentinel\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("accepted"));
    }

    @Test
    void invalidRequestReturnsFieldValidationError() throws Exception {
        mockMvc.perform(post("/mvc-test")
                        .contentType("application/json")
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void malformedJsonReturnsSafeBadRequest() throws Exception {
        mockMvc.perform(post("/mvc-test")
                        .contentType("application/json")
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("invalid JSON")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))));
    }

    @RestController
    static class ValidationController {

        @PostMapping("/mvc-test")
        ResponseEntity<String> accept(@Valid @RequestBody Input input) {
            return ResponseEntity.ok("accepted");
        }
    }

    record Input(@NotBlank String name) {
    }
}
