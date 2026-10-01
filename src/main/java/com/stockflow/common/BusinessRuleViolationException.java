package com.stockflow.common;

/**
 * The request is well formed but asks for something the business does not allow,
 * for example a transfer from a store to itself (HTTP 422).
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super("Business rule violated", message);
    }
}
