package com.acme.blueprint.users.web;

import java.net.URI;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.users.application.UserFinder;
import com.acme.blueprint.users.application.UserManagement;
import com.acme.blueprint.users.application.UserSearchCriteria;
import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserStatus;
import com.acme.blueprint.users.domain.UserTier;
import com.acme.blueprint.users.domain.NationalCode;
import com.acme.blueprint.shared.ApiResponse;
import com.acme.blueprint.shared.EntityTags;
import com.acme.blueprint.shared.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST adapter for the users module. It translates HTTP into use-case
 * calls and domain objects into response bodies — nothing more.
 *
 * <pre>
 * GET  /api/users                       list: search, status, tier, page, size, sort=createdAt|lastName[,asc|desc]
 * POST /api/users                       register                         → 201, Location, ETag
 * GET  /api/users/{userId}          read                             → ETag
 * PUT  /api/users/{userId}/profile  replace the profile  (If-Match)  → ETag
 * PUT  /api/users/{userId}/status   change the status    (If-Match)  → ETag
 * </pre>
 *
 * The two PUT sub-resources are write-only commands that return the updated
 * user. Both require {@code If-Match} with the ETag last read, so two
 * operators editing the same user cannot silently overwrite each other.
 */
@RestController
@RequestMapping("/api/users")
class UserController {

    private static final int SEARCH_MAX_LENGTH = 100;

    private final UserManagement users;

    UserController(UserManagement users) {
        this.users = users;
    }

    /** Sort keys are the finder's public vocabulary ({@link UserFinder}), never entity paths. */
    @GetMapping
    ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) @Size(max = SEARCH_MAX_LENGTH) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserTier tier,
            @PageableDefault(size = 20, sort = UserFinder.SORT_BY_CREATED_AT, direction = Sort.Direction.DESC)
                    Pageable pageable) {
        var page = users.search(new UserSearchCriteria(search, status, tier), pageable)
                .map(UserResponse::from);
        return ApiResponse.of(PageResponse.from(page));
    }

    @PostMapping
    ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterUserRequest request) {
        User user = users.register(NationalCode.of(request.nationalCode()), request.profile().toDomain());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{userId}")
                .buildAndExpand(user.id())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(EntityTags.of(user.version()))
                .body(ApiResponse.of(UserResponse.from(user)));
    }

    @GetMapping("/{userId}")
    ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UserId userId) {
        return withETag(users.get(userId));
    }

    @PutMapping("/{userId}/profile")
    ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @PathVariable UserId userId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody UserProfileDto profile) {
        return withETag(users.updateProfile(userId, EntityTags.parseVersion(ifMatch), profile.toDomain()));
    }

    @PutMapping("/{userId}/status")
    ResponseEntity<ApiResponse<UserResponse>> changeStatus(
            @PathVariable UserId userId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        return withETag(users.changeStatus(userId, EntityTags.parseVersion(ifMatch), request.status()));
    }

    private static ResponseEntity<ApiResponse<UserResponse>> withETag(User user) {
        return ResponseEntity.ok()
                .eTag(EntityTags.of(user.version()))
                .body(ApiResponse.of(UserResponse.from(user)));
    }
}
