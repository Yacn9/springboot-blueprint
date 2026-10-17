package com.acme.blueprint.notifications.domain;

public enum NotificationKind {
    /** Sent automatically, once per user, after registration. */
    WELCOME,
    /** Composed and sent by an operator. */
    AD_HOC
}
