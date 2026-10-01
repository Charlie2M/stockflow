package com.stockflow.common;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Validation rules that span several fields of a request, which Bean Validation cannot express. */
public final class RequestRules {

    private RequestRules() {
    }

    public static void requireUniqueSkus(Collection<String> skus) {
        Set<String> seen = new HashSet<>();
        for (String sku : skus) {
            if (!seen.add(sku)) {
                throw new BusinessRuleViolationException(
                        "SKU '%s' appears more than once in the request".formatted(sku));
            }
        }
    }
}
