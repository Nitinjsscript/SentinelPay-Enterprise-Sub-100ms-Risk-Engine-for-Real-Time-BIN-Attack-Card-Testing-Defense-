package com.example.demo.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

import com.example.demo.exception.InvalidPatchRequestException;

final class PatchRequestValidator {

    enum ValueType {
        STRING,
        BOOLEAN,
        NUMBER,
        INTEGER,
        OBJECT,
        LOCAL_DATE_TIME
    }

    private PatchRequestValidator() {
    }

    static void validate(Map<String, Object> updates, Map<String, ValueType> schema) {
        if (updates == null || updates.isEmpty()) {
            throw new InvalidPatchRequestException("At least one supported field is required.");
        }

        updates.forEach((field, value) -> {
            ValueType expectedType = schema.get(field);
            if (expectedType == null) {
                throw new InvalidPatchRequestException("Unsupported PATCH field: " + field);
            }
            if (!matches(expectedType, value)) {
                throw new InvalidPatchRequestException("Invalid value type for PATCH field: " + field);
            }
        });
    }

    private static boolean matches(ValueType expectedType, Object value) {
        if (value == null) {
            return false;
        }

        return switch (expectedType) {
            case STRING -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
            case NUMBER -> value instanceof Number number && isDecimal(number);
            case INTEGER -> value instanceof Number number && isInteger(number);
            case OBJECT -> value instanceof Map<?, ?>;
            case LOCAL_DATE_TIME -> isLocalDateTime(value);
        };
    }

    private static boolean isInteger(Number number) {
        try {
            BigDecimal value = new BigDecimal(number.toString());
            return value.stripTrailingZeros().scale() <= 0
                    && value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0
                    && value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isDecimal(Number number) {
        try {
            new BigDecimal(number.toString());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isLocalDateTime(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            LocalDateTime.parse(text);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
