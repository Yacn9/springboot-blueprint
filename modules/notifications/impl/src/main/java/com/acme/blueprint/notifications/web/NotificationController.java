package com.acme.blueprint.notifications.web;

import java.net.URI;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.notifications.application.NotificationManagement;
import com.acme.blueprint.notifications.domain.Notification;
import com.acme.blueprint.notifications.domain.NotificationId;
import com.acme.blueprint.shared.ApiResponse;
import com.acme.blueprint.shared.PageResponse;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * <pre>
 * GET  /api/notifications?userId=…&amp;page=0&amp;size=20   a user's notifications, newest first
 * GET  /api/notifications/{notificationId}
 * POST /api/notifications                                send an ad-hoc notification → 201, Location
 * </pre>
 *
 * Paging behaves exactly like the users list. The order is fixed, so any
 * {@code sort} parameter is rejected rather than silently ignored.
 * Notifications are immutable, so they carry no ETag.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationManagement notifications;

    NotificationController(NotificationManagement notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    ApiResponse<PageResponse<NotificationResponse>> listForUser(
            @RequestParam UserId userId, @PageableDefault(size = 20) Pageable pageable) {
        var page = notifications.findForUser(userId, pageable).map(NotificationResponse::from);
        return ApiResponse.of(PageResponse.from(page));
    }

    @GetMapping("/{notificationId}")
    ApiResponse<NotificationResponse> get(@PathVariable NotificationId notificationId) {
        return ApiResponse.of(NotificationResponse.from(notifications.get(notificationId)));
    }

    @PostMapping
    ResponseEntity<ApiResponse<NotificationResponse>> send(@Valid @RequestBody SendNotificationRequest request) {
        Notification notification = notifications.sendToUser(
                new UserId(request.userId()), request.subject(), request.body());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{notificationId}")
                .buildAndExpand(notification.id())
                .toUri();
        return ResponseEntity.created(location).body(ApiResponse.of(NotificationResponse.from(notification)));
    }
}
