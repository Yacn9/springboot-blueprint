package com.acme.blueprint.notifications.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.acme.blueprint.users.UserId;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A message to one user. It references the user by id only — there
 * is no foreign key into the users schema, because modules own their
 * data and may only meet through contracts.
 *
 * <p>The content rules below are the single source of truth; the web DTO
 * references the same constants.
 */
@Entity
@Table(name = "notification", schema = "notifications")
public class Notification {

    public static final int RECIPIENT_MAX_LENGTH = 254;
    public static final int SUBJECT_MAX_LENGTH = 200;
    public static final int BODY_MAX_LENGTH = 2000;
    /** Same shape rule as the users module; each module owns its own copy rather than sharing a type. */
    public static final String RECIPIENT_PATTERN = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    private static final Pattern RECIPIENT = Pattern.compile(RECIPIENT_PATTERN);

    @Id
    private UUID id;

    @Version
    private Long version;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private NotificationKind kind;

    private String recipient;

    private String subject;

    private String body;

    private Instant createdAt;

    private Instant sentAt;

    /** For Hibernate only. */
    protected Notification() {
    }

    private Notification(
            UserId userId, NotificationKind kind, String recipient, String subject, String body, Instant now) {
        this.id = NotificationId.newId().value();
        this.userId = userId.value();
        this.kind = kind;
        this.recipient = requireText("recipient", recipient, RECIPIENT_MAX_LENGTH);
        if (!RECIPIENT.matcher(recipient).matches()) {
            throw new InvalidNotificationException("recipient", "must be an email address");
        }
        this.subject = requireText("subject", subject, SUBJECT_MAX_LENGTH);
        this.body = requireText("body", body, BODY_MAX_LENGTH);
        this.createdAt = now;
    }

    /**
     * Parameters follow {@code UserRegistered}'s order, so the listener maps
     * field for field. In both factories the address comes right before the
     * timestamp.
     */
    public static Notification welcome(UserId userId, String fullName, String email, Instant now) {
        return new Notification(userId, NotificationKind.WELCOME, email,
                "Welcome aboard", "Hello %s, your account has been created.".formatted(fullName), now);
    }

    public static Notification adHoc(UserId userId, String subject, String body, String email, Instant now) {
        return new Notification(userId, NotificationKind.AD_HOC, email, subject, body, now);
    }

    public void markSent(Instant now) {
        this.sentAt = now;
    }

    private static String requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new InvalidNotificationException(field, "must be non-blank and at most " + maxLength + " characters");
        }
        return value;
    }

    public NotificationId id() {
        return new NotificationId(id);
    }

    public UserId userId() {
        return new UserId(userId);
    }

    public NotificationKind kind() {
        return kind;
    }

    public String recipient() {
        return recipient;
    }

    public String subject() {
        return subject;
    }

    public String body() {
        return body;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** {@code null} until delivery succeeded. */
    public Instant sentAt() {
        return sentAt;
    }
}
