package com.acme.blueprint.users.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.users.application.UserManagement;
import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserProfile;
import com.acme.blueprint.users.domain.UserTier;
import com.acme.blueprint.users.domain.NationalCode;
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
@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final String PROFILE = """
            { "firstName": "Vesen", "lastName": "Naqibi", "mobile": "09121234567",
              "email": "vesen@example.com", "tier": "GOLD", "branch": "Tehran Central" }
            """;

    private final User user = User.register(
            NationalCode.of("0012345679"),
            new UserProfile("Vesen", "Naqibi", "09121234567", "vesen@example.com", UserTier.GOLD, "Tehran Central", null),
            Instant.parse("2026-10-17T08:00:00Z"));

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    UserManagement users;

    @Test
    void registersAUserAndReturnsItWithLocationAndETag() {
        given(users.register(any(), any())).willReturn(user);

        var result = mvc.post().uri("/api/users?source=import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"nationalCode\": \"0012345679\", \"profile\": " + PROFILE + " }")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers()
                .hasValue(HttpHeaders.LOCATION, "http://localhost/api/users/" + user.id())
                .hasValue(HttpHeaders.ETAG, "\"0\"");
        assertThat(result).bodyJson().extractingPath("$.data.status").isEqualTo("PENDING");
        assertThat(result).bodyJson().extractingPath("$.data.profile.firstName").isEqualTo("Vesen");
    }

    @Test
    void passesTheIfMatchVersionToTheUseCase() {
        given(users.updateProfile(eq(user.id()), eq(4L), any())).willReturn(user);

        var result = putProfile("\"4\"");

        assertThat(result).hasStatusOk();
        assertThat(result).headers().hasValue(HttpHeaders.ETAG, "\"0\"");
    }

    /** Compressing proxies weaken ETags; the version is still the version. */
    @Test
    void acceptsAWeakETag() {
        given(users.updateProfile(eq(user.id()), eq(4L), any())).willReturn(user);

        assertThat(putProfile("W/\"4\"")).hasStatusOk();
    }

    @Test
    void requiresUpdatesToBeConditional() {
        var result = putProfile(null);

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.precondition-required");
        verifyNoInteractions(users);
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
        given(users.updateProfile(any(), anyLong(), any())).willThrow(new OptimisticLockingFailureException("race"));

        var result = putProfile("\"0\"");

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.version-mismatch");
    }

    @Test
    void reportsEveryInvalidFieldAsAProblemDetail() {
        var result = mvc.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "nationalCode": "12", "profile": { "firstName": "", "lastName": "Naqibi",
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
        var result = mvc.put().uri("/api/users/{userId}/profile", user.id())
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
        var result = mvc.get().uri("/api/users?search=" + "x".repeat(101)).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("search");
        verifyNoInteractions(users);
    }

    @Test
    void translatesAMissingUserInto404WithItsCode() {
        UserId id = UserId.newId();
        given(users.get(id)).willThrow(new ResourceNotFoundException("users.not-found", "gone") {});

        var result = mvc.get().uri("/api/users/{userId}", id).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("users.not-found");
    }

    @Test
    void givesFrameworkErrorsACodeToo() {
        var malformedId = mvc.get().uri("/api/users/not-a-uuid").exchange();
        var unknownRoute = mvc.get().uri("/api/nothing-here").exchange();

        assertThat(malformedId).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(malformedId).bodyJson().extractingPath("$.code").isEqualTo("request.invalid-parameter");
        assertThat(unknownRoute).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(unknownRoute).bodyJson().extractingPath("$.code").isEqualTo("request.not-found");
    }

    @Test
    void keepsTheStatusOfAnnotatedExceptions() {
        given(users.get(any())).willThrow(new ArchivedException());

        var result = mvc.get().uri("/api/users/{userId}", user.id()).exchange();

        assertThat(result).hasStatus(HttpStatus.GONE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void hidesUnexpectedFailuresBehindAGeneric500() throws Exception {
        given(users.changeStatus(any(), anyLong(), any())).willThrow(new IllegalStateException("db password is hunter2"));

        var result = mvc.put().uri("/api/users/{userId}/status", user.id())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"ACTIVE\" }")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("server.internal-error");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("hunter2");
    }

    private MvcTestResult putProfile(String ifMatch) {
        var request = mvc.put().uri("/api/users/{userId}/profile", user.id())
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
