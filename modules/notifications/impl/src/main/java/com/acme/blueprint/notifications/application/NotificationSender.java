package com.acme.blueprint.notifications.application;

import com.acme.blueprint.notifications.domain.Notification;

/**
 * Outbound port to whatever actually delivers messages — SMTP, an SMS
 * gateway, a push service. Implementations live in {@code infrastructure};
 * swapping one never touches the use cases.
 *
 * <p>Delivery is <strong>at-least-once</strong>: a send can be repeated if
 * the surrounding transaction fails to commit afterwards. Implementations
 * should pass {@link Notification#id()} to the provider as an idempotency key
 * where the provider supports one. When a channel is slow or must never
 * repeat, move the send after commit: store the notification unsent, publish
 * an internal event, and deliver from an {@code @ApplicationModuleListener}.
 */
public interface NotificationSender {

    /** Delivers the notification or throws; throwing rolls the use case back. */
    void send(Notification notification);
}
