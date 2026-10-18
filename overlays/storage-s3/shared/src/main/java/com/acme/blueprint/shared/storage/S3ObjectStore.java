package com.acme.blueprint.shared.storage;

import java.util.Optional;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** The {@link ObjectStore} on one bucket of an S3-compatible server. */
final class S3ObjectStore implements ObjectStore {

    private static final int NOT_FOUND = 404;

    private final S3Client s3;
    private final String bucket;

    S3ObjectStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /** Creates the bucket on first start; a bucket that exists is left as it is. */
    void createBucketIfMissing() {
        try {
            s3.headBucket(request -> request.bucket(bucket));
        } catch (NoSuchBucketException missing) {
            s3.createBucket(request -> request.bucket(bucket));
        } catch (S3Exception failure) {
            if (failure.statusCode() != NOT_FOUND) {
                throw failure;
            }
            s3.createBucket(request -> request.bucket(bucket));
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        s3.putObject(request -> request.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(content));
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(request -> request.bucket(bucket).key(key));
            return Optional.of(new StoredObject(object.asByteArray(), object.response().contentType()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(request -> request.bucket(bucket).key(key));
    }
}
