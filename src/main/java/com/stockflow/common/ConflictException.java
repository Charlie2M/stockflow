package com.stockflow.common;

import java.util.Map;

/** The request is valid but clashes with the current state of the data (HTTP 409). */
public class ConflictException extends DomainException {

    public ConflictException(String title, String message) {
        super(title, message);
    }

    protected ConflictException(String title, String message, Map<String, Object> details) {
        super(title, message, details);
    }
}
