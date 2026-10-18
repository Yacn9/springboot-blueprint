package com.acme.blueprint.shared.storage;

import java.util.Optional;

/**
 * Files kept outside the database — uploads, documents, exports — in one
 * bucket of an S3-compatible object storage. Modules depend on this port,
 * never on the storage server: which one runs (MinIO, SeaweedFS, RustFS, a
 * cloud's S3) is configuration, {@code blueprint.storage} in the platform
 * defaults.
 *
 * <p>Give each module its own key prefix ({@code users/…}), as each owns its
 * own database schema: a module never reads another module's objects.
 */
public interface ObjectStore {

    /** Stores {@code content} under {@code key}, replacing whatever was there. */
    void put(String key, byte[] content, String contentType);

    /** The object under {@code key}; empty when there is none. */
    Optional<StoredObject> get(String key);

    /** Removes the object under {@code key}; nothing happens when there is none. */
    void delete(String key);
}
