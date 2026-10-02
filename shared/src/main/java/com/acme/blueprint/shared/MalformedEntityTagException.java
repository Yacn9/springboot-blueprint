package com.acme.blueprint.shared;

public final class MalformedEntityTagException extends InvalidRequestException {

    MalformedEntityTagException(String header) {
        super("request.invalid-if-match",
                "If-Match must be the ETag of a previous response, e.g. \"3\" (wildcards are not supported), but was: "
                        + header);
    }
}
