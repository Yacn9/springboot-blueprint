package com.acme.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.blueprint.shared.testing.DatabaseTestcontainers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The whole application against a real database: the platform migrations run,
 * Hibernate validates every mapping and the HTTP layer answers. Add a test
 * here that crosses your modules once there are two that talk.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseTestcontainers.class)
class BlueprintApplicationTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void startsAndReportsHealthy() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void servesTheOpenApiDocument() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
    }
}
