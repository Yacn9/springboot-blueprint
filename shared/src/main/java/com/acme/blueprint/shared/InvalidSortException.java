package com.acme.blueprint.shared;

import java.util.Collection;
import java.util.TreeSet;

public final class InvalidSortException extends InvalidRequestException {

    InvalidSortException(String key, Collection<String> allowedKeys) {
        super("request.invalid-sort",
                "Cannot sort by '%s'; allowed keys are %s".formatted(key, new TreeSet<>(allowedKeys)));
    }
}
