package com.acme.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.acme.blueprint.shared.storage.ObjectStore;
import com.acme.blueprint.shared.testing.DatabaseTestcontainers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The object storage against a real server, the one compose.yaml runs: the
 * bucket is created on startup, and an object goes in, comes back with its
 * content type, and goes away.
 */
@SpringBootTest
@Import(DatabaseTestcontainers.class)
class ObjectStoreTests {

    @Autowired
    ObjectStore objects;

    @Test
    void storesReadsAndDeletesAnObject() {
        byte[] content = "hello, storage".getBytes(StandardCharsets.UTF_8);

        objects.put("tests/hello.txt", content, "text/plain");

        assertThat(objects.get("tests/hello.txt")).hasValueSatisfying(stored -> {
            assertThat(stored.content()).isEqualTo(content);
            assertThat(stored.contentType()).isEqualTo("text/plain");
        });

        objects.delete("tests/hello.txt");

        assertThat(objects.get("tests/hello.txt")).isEmpty();
    }

    @Test
    void findsNothingUnderAKeyNeverWritten() {
        assertThat(objects.get("tests/never-written")).isEmpty();
    }
}
