package com.acme.blueprint.notifications.domain;

public enum NotificationKind {
    /** Sent automatically, once per customer, after registration. */
    WELCOME,
    /** Composed and sent by an operator. */
    AD_HOC
}
