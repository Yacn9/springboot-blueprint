package com.acme.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import com.acme.blueprint.shared.testing.DatabaseTestcontainers;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The whole application against a real database: every module's Flyway
 * migrations run together, Hibernate validates every mapping, and a request
 * travels from one module to the other through the event registry.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseTestcontainers.class)
class BlueprintApplicationTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void registeringACustomerSendsAWelcomeNotification() throws Exception {
        var registered = mvc.post().uri("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nationalCode": "0012345679",
                          "profile": {
                            "firstName": "Ali", "lastName": "Rezaei", "mobile": "09121234567",
                            "email": "ali@example.com", "tier": "GOLD", "branch": "Tehran Central"
                          }
                        }
                        """)
                .exchange();
        assertThat(registered).hasStatus(HttpStatus.CREATED);
        String customerId = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.id");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(mvc.get().uri("/api/notifications").param("customerId", customerId))
                        .hasStatusOk()
                        .bodyJson().extractingPath("$.data.items[*].kind").asArray().containsExactly("WELCOME"));
    }

    @Test
    void servesTheOpenApiDocument() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
    }
}
