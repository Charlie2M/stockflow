package com.stockflow.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for errors caused by a broken business rule (as opposed to a bug).
 * <p>
 * Domain code throws these without knowing anything about HTTP. The web layer
 * ({@code ApiExceptionHandler}) translates them into RFC 9457 "problem details" responses,
 * using {@link #getTitle()} as the title and {@link #getDetails()} as extra JSON fields.
 */
public abstract class DomainException extends RuntimeException {

    private final String title;
    private final Map<String, Object> details;

    protected DomainException(String title, String message) {
        this(title, message, Map.of());
    }

    protected DomainException(String title, String message, Map<String, Object> details) {
        super(message);
        this.title = title;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public String getTitle() {
        return title;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
