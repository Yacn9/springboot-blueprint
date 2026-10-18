package com.acme.blueprint.shared.storage;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The S3 client, pointed at {@code blueprint.storage.endpoint}. Spring Boot
 * has no object storage support of its own, so this is it: path-style
 * addressing ({@code endpoint/bucket/key}), which every S3-compatible server
 * understands, and checksums only where S3 requires them — the SDK's newer
 * default trailing checksums are not understood by every server.
 */
@Configuration(proxyBeanMethods = false)
class ObjectStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(
            @Value("${blueprint.storage.endpoint}") URI endpoint,
            @Value("${blueprint.storage.region}") String region,
            @Value("${blueprint.storage.access-key}") String accessKey,
            @Value("${blueprint.storage.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    /** ponytail: creates its bucket on startup; drop that where the bucket is provisioned with the infrastructure. */
    @Bean
    ObjectStore objectStore(S3Client s3, @Value("${blueprint.storage.bucket}") String bucket) {
        S3ObjectStore store = new S3ObjectStore(s3, bucket);
        store.createBucketIfMissing();
        return store;
    }
}
