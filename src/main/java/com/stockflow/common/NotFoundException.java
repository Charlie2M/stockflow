package com.stockflow.common;

/** A product, store or transfer that the client referenced does not exist (HTTP 404). */
public class NotFoundException extends DomainException {

    public NotFoundException(String resource, Object key) {
        super("Resource not found", "%s '%s' was not found".formatted(resource, key));
    }
}
