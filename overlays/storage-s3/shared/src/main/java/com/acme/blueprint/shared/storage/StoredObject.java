package com.acme.blueprint.shared.storage;

/**
 * An object read back from the {@link ObjectStore}: its bytes and the content
 * type it was stored with.
 *
 * <p>ponytail: whole objects in memory; add a streaming read when objects
 * outgrow the heap.
 */
public record StoredObject(byte[] content, String contentType) {
}
