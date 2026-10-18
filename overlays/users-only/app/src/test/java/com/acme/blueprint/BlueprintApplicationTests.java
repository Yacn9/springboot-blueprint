package com.acme.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

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
 * travels through the HTTP layer to the database and back. Add a test here
 * that crosses your modules once there are two that talk.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseTestcontainers.class)
class BlueprintApplicationTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void registersAUserAndReadsItBack() throws Exception {
        var registered = mvc.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nationalCode": "0012345679",
                          "profile": {
                            "firstName": "Vesen", "lastName": "Naqibi", "mobile": "09121234567",
                            "email": "vesen@example.com", "tier": "GOLD", "branch": "Tehran Central"
                          }
                        }
                        """)
                .exchange();
        assertThat(registered).hasStatus(HttpStatus.CREATED);
        String userId = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.id");

        assertThat(mvc.get().uri("/api/users/{userId}", userId))
                .hasStatusOk()
                .bodyJson().extractingPath("$.data.profile.email").isEqualTo("vesen@example.com");
    }

    @Test
    void servesTheOpenApiDocument() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
    }
}
