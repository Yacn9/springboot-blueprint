package com.acme.blueprint.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.acme.blueprint.customers.CustomerDirectory;
import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.customers.CustomerRegistered;
import com.acme.blueprint.customers.CustomerSummary;
import com.acme.blueprint.notifications.application.NotificationManagement;
import com.acme.blueprint.notifications.application.UnknownCustomerException;
import com.acme.blueprint.notifications.domain.InvalidNotificationException;
import com.acme.blueprint.notifications.domain.Notification;
import com.acme.blueprint.notifications.domain.NotificationKind;
import com.acme.blueprint.shared.testing.DatabaseTestcontainers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the notifications module alone. The customers module is not even on
 * the classpath — only its -api contract is — so the directory is mocked and
 * the event is published by the test, exactly as the real module would.
 */
@ApplicationModuleTest
@Import(DatabaseTestcontainers.class)
class NotificationsModuleTests {

    @MockitoBean
    CustomerDirectory customers;

    @Autowired
    NotificationManagement notifications;

    @Test
    void welcomesANewlyRegisteredCustomer(Scenario scenario) {
        CustomerId id = CustomerId.newId();

        scenario.publish(new CustomerRegistered(id, "Ali Rezaei", "ali@example.com", Instant.now()))
                .andWaitForStateChange(() -> sentTo(id), list -> !list.isEmpty())
                .andVerify((List<Notification> sent) -> assertThat(sent).singleElement().satisfies(notification -> {
                    assertThat(notification.kind()).isEqualTo(NotificationKind.WELCOME);
                    assertThat(notification.recipient()).isEqualTo("ali@example.com");
                    assertThat(notification.body()).contains("Ali Rezaei");
                    assertThat(notification.sentAt()).isNotNull();
                }));
    }

    @Test
    void welcomesEachCustomerOnlyOnceWhenTheEventIsRedelivered() {
        CustomerId id = CustomerId.newId();

        notifications.sendWelcome(id, "Twice", "twice@example.com");
        notifications.sendWelcome(id, "Twice", "twice@example.com");

        assertThat(sentTo(id)).hasSize(1);
    }

    @Test
    void looksUpTheRecipientThroughTheCustomerDirectory() {
        CustomerId id = CustomerId.newId();
        given(customers.findById(id))
                .willReturn(Optional.of(new CustomerSummary(id, "Sara Ahmadi", "sara@example.com", "09121234567")));

        Notification notification = notifications.sendToCustomer(id, "Statement ready", "Your statement is ready.");

        assertThat(notification.recipient()).isEqualTo("sara@example.com");
        assertThat(notifications.get(notification.id()).kind()).isEqualTo(NotificationKind.AD_HOC);
    }

    @Test
    void refusesToNotifyAnUnknownCustomer() {
        CustomerId id = CustomerId.newId();
        given(customers.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notifications.sendToCustomer(id, "Hello", "Body"))
                .isInstanceOf(UnknownCustomerException.class);
    }

    @Test
    void enforcesContentRulesOutsideTheWebLayerToo() {
        CustomerId id = CustomerId.newId();
        given(customers.findById(id))
                .willReturn(Optional.of(new CustomerSummary(id, "Sara Ahmadi", "sara@example.com", "09121234567")));

        assertThatThrownBy(() -> notifications.sendToCustomer(id, "x".repeat(Notification.SUBJECT_MAX_LENGTH + 1), "Body"))
                .isInstanceOf(InvalidNotificationException.class);
        assertThat(sentTo(id)).isEmpty();
    }

    private List<Notification> sentTo(CustomerId id) {
        return notifications.findForCustomer(id, PageRequest.of(0, 10)).getContent();
    }
}
