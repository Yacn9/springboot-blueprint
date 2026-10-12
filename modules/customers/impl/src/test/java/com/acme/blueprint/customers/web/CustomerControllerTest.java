package com.acme.blueprint.customers.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.customers.application.CustomerManagement;
import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerProfile;
import com.acme.blueprint.customers.domain.CustomerTier;
import com.acme.blueprint.customers.domain.NationalCode;
import com.acme.blueprint.shared.ResourceNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Web slice: HTTP mapping, validation, concurrency headers and error
 * translation, with the use cases mocked. No database, no other modules.
 */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final String PROFILE = """
            { "firstName": "Ali", "lastName": "Rezaei", "mobile": "09121234567",
              "email": "ali@example.com", "tier": "GOLD", "branch": "Tehran Central" }
            """;

    private final Customer customer = Customer.register(
            NationalCode.of("0012345679"),
            new CustomerProfile("Ali", "Rezaei", "09121234567", "ali@example.com", CustomerTier.GOLD, "Tehran Central", null),
            Instant.parse("2026-10-17T08:00:00Z"));

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    CustomerManagement customers;

    @Test
    void registersACustomerAndReturnsItWithLocationAndETag() {
        given(customers.register(any(), any())).willReturn(customer);

        var result = mvc.post().uri("/api/customers?source=import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"nationalCode\": \"0012345679\", \"profile\": " + PROFILE + " }")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers()
                .hasValue(HttpHeaders.LOCATION, "http://localhost/api/customers/" + customer.id())
                .hasValue(HttpHeaders.ETAG, "\"0\"");
        assertThat(result).bodyJson().extractingPath("$.data.status").isEqualTo("PENDING");
        assertThat(result).bodyJson().extractingPath("$.data.profile.firstName").isEqualTo("Ali");
    }

    @Test
    void passesTheIfMatchVersionToTheUseCase() {
        given(customers.updateProfile(eq(customer.id()), eq(4L), any())).willReturn(customer);

        var result = putProfile("\"4\"");

        assertThat(result).hasStatusOk();
        assertThat(result).headers().hasValue(HttpHeaders.ETAG, "\"0\"");
    }

    /** Compressing proxies weaken ETags; the version is still the version. */
    @Test
    void acceptsAWeakETag() {
        given(customers.updateProfile(eq(customer.id()), eq(4L), any())).willReturn(customer);

        assertThat(putProfile("W/\"4\"")).hasStatusOk();
    }

    @Test
    void requiresUpdatesToBeConditional() {
        var result = putProfile(null);

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.precondition-required");
        verifyNoInteractions(customers);
    }

    @Test
    void refusesAWildcardIfMatch() {
        var result = putProfile("*");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid-if-match");
    }

    /** A commit-time @Version clash is the same situation as a stale If-Match: same 412, same code. */
    @Test
    void reportsAConcurrentCommitLikeAStaleETag() {
        given(customers.updateProfile(any(), anyLong(), any())).willThrow(new OptimisticLockingFailureException("race"));

        var result = putProfile("\"0\"");

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.version-mismatch");
    }

    @Test
    void reportsEveryInvalidFieldAsAProblemDetail() {
        var result = mvc.post().uri("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "nationalCode": "12", "profile": { "firstName": "", "lastName": "Rezaei",
                          "mobile": "123", "email": "not-an-email", "tier": "GOLD", "branch": "Tehran" } }
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder(
                "nationalCode", "profile.firstName", "profile.mobile", "profile.email");
    }

    @Test
    void namesTheFieldWhenAnEnumValueIsUnknown() {
        var result = mvc.put().uri("/api/customers/{customerId}/profile", customer.id())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PROFILE.replace("\"GOLD\"", "\"gold\""))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("tier");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").asString().contains("GOLD");
    }

    /** Query-parameter constraints produce the same shape as body validation. */
    @Test
    void namesTheParameterWhenAQueryParameterIsInvalid() {
        var result = mvc.get().uri("/api/customers?search=" + "x".repeat(101)).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("search");
        verifyNoInteractions(customers);
    }

    @Test
    void translatesAMissingCustomerInto404WithItsCode() {
        CustomerId id = CustomerId.newId();
        given(customers.get(id)).willThrow(new ResourceNotFoundException("customers.not-found", "gone") {});

        var result = mvc.get().uri("/api/customers/{customerId}", id).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("customers.not-found");
    }

    @Test
    void givesFrameworkErrorsACodeToo() {
        var malformedId = mvc.get().uri("/api/customers/not-a-uuid").exchange();
        var unknownRoute = mvc.get().uri("/api/nothing-here").exchange();

        assertThat(malformedId).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(malformedId).bodyJson().extractingPath("$.code").isEqualTo("request.invalid-parameter");
        assertThat(unknownRoute).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(unknownRoute).bodyJson().extractingPath("$.code").isEqualTo("request.not-found");
    }

    @Test
    void keepsTheStatusOfAnnotatedExceptions() {
        given(customers.get(any())).willThrow(new ArchivedException());

        var result = mvc.get().uri("/api/customers/{customerId}", customer.id()).exchange();

        assertThat(result).hasStatus(HttpStatus.GONE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void hidesUnexpectedFailuresBehindAGeneric500() throws Exception {
        given(customers.changeStatus(any(), anyLong(), any())).willThrow(new IllegalStateException("db password is hunter2"));

        var result = mvc.put().uri("/api/customers/{customerId}/status", customer.id())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"ACTIVE\" }")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("server.internal-error");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("hunter2");
    }

    private MvcTestResult putProfile(String ifMatch) {
        var request = mvc.put().uri("/api/customers/{customerId}/profile", customer.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PROFILE);
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    @ResponseStatus(HttpStatus.GONE)
    static class ArchivedException extends RuntimeException {
    }
}
