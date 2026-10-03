package com.acme.blueprint.shared;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optimistic-concurrency handshake over HTTP: responses of resources that
 * accept {@code PUT} carry the aggregate's {@code @Version} as an {@code ETag};
 * state-changing requests send it back in {@code If-Match}. A stale value
 * means someone else changed the resource in between, and the change is
 * refused instead of silently overwriting theirs.
 *
 * <p>Weak tags ({@code W/"3"}) are accepted on purpose: compressing proxies
 * weaken strong tags, and a version number identifies the state just as well
 * either way. {@code If-Match: *} is not supported — send the tag you read.
 */
public final class EntityTags {

    private static final Pattern VERSION_TAG = Pattern.compile("(?:W/)?\"(\\d+)\"");

    private EntityTags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /** @throws MalformedEntityTagException unless the header is one tag produced by {@link #of} */
    public static long parseVersion(String ifMatch) {
        Matcher matcher = VERSION_TAG.matcher(ifMatch == null ? "" : ifMatch.trim());
        if (!matcher.matches()) {
            throw new MalformedEntityTagException(ifMatch);
        }
        return Long.parseLong(matcher.group(1));
    }
}
