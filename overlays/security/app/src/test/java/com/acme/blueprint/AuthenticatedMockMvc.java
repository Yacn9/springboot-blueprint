package com.acme.blueprint;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

/**
 * MockMvc requests in this project's tests carry a JWT: they are about what
 * the application does, not who may call it — SecurityTests covers that.
 */
@Component
class AuthenticatedMockMvc implements MockMvcBuilderCustomizer {

    @Override
    public void customize(ConfigurableMockMvcBuilder<?> builder) {
        builder.defaultRequest(MockMvcRequestBuilders.get("/").with(jwt()));
    }
}
