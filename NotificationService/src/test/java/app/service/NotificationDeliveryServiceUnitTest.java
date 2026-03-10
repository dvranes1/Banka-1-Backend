package app.service;

import app.dto.NotificationRequest;
import app.dto.ResolvedEmail;
import app.dto.RetryTask;
import app.entities.NotificationDelivery;
import app.entities.NotificationDeliveryStatus;
import app.entities.NotificationType;
import app.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationDeliveryService}.
 *
 * <p>These tests validate DB state transitions and retry scheduling logic.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceUnitTest {

    /**
     * Test email used in assertions and payload examples.
     */
    private static final String TEST_EMAIL = "dimitrije.tomic99@gmail.com";

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RetryTaskQueue retryTaskQueue;

    @InjectMocks
    private NotificationDeliveryService notificationDeliveryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationDeliveryService, "defaultMaxRetries", 4);
        ReflectionTestUtils.setField(notificationDeliveryService, "retryDelaySeconds", 5);
        lenient().when(notificationDeliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void handleIncomingMessageCreatesNewDeliveryAndMarksSucceededOnSendSuccess() {
        NotificationRequest request = new NotificationRequest("Dimitrije", TEST_EMAIL, Map.of("subject", "Hello", "body", "Body"));
        when(notificationService.resolveEmailContent(request, NotificationType.EMPLOYEE_CREATED))
                .thenReturn(new ResolvedEmail(TEST_EMAIL, "Hello", "Body"));

        notificationDeliveryService.handleIncomingMessage(request, NotificationType.EMPLOYEE_CREATED);

        verify(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body", NotificationType.EMPLOYEE_CREATED);
        ArgumentCaptor<NotificationDelivery> saveCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository, atLeastOnce()).save(saveCaptor.capture());

        NotificationDelivery firstSaved = saveCaptor.getAllValues().get(0);
        assertNotNull(firstSaved.getDeliveryId());
        UUID.fromString(firstSaved.getDeliveryId());

        NotificationDelivery finalSaved = saveCaptor.getValue();
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, finalSaved.getStatus());
        assertEquals(0, finalSaved.getRetryCount());
        assertNotNull(finalSaved.getSentAt());
        assertEquals(NotificationType.EMPLOYEE_CREATED, finalSaved.getNotificationType());
    }

    @Test
    void handleIncomingMessageSchedulesRetryUsingConfiguredDefaultDelayOnSendFailure() {
        NotificationRequest request = new NotificationRequest("Dimitrije", TEST_EMAIL, Map.of("subject", "Hello", "body", "Body"));
        when(notificationService.resolveEmailContent(request, NotificationType.EMPLOYEE_CREATED))
                .thenReturn(new ResolvedEmail(TEST_EMAIL, "Hello", "Body"));
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body", NotificationType.EMPLOYEE_CREATED);

        notificationDeliveryService.handleIncomingMessage(request, NotificationType.EMPLOYEE_CREATED);

        ArgumentCaptor<NotificationDelivery> saveCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository, atLeastOnce()).save(saveCaptor.capture());
        NotificationDelivery finalSaved = saveCaptor.getValue();

        assertEquals(NotificationDeliveryStatus.RETRY_SCHEDULED, finalSaved.getStatus());
        assertEquals(1, finalSaved.getRetryCount());
        assertNotNull(finalSaved.getNextAttemptAt());
        assertNotNull(finalSaved.getLastAttemptAt());
        assertEquals(5, finalSaved.getNextAttemptAt().getEpochSecond() - finalSaved.getLastAttemptAt().getEpochSecond());

        verify(retryTaskQueue).schedule(finalSaved.getDeliveryId(), finalSaved.getNextAttemptAt());
    }

    @Test
    void handleIncomingMessageFailsImmediatelyOnMailAuthenticationException() {
        NotificationRequest request = new NotificationRequest("Dimitrije", TEST_EMAIL, Map.of("subject", "Hello", "body", "Body"));
        when(notificationService.resolveEmailContent(request, NotificationType.EMPLOYEE_CREATED))
                .thenReturn(new ResolvedEmail(TEST_EMAIL, "Hello", "Body"));
        doThrow(new MailAuthenticationException("Authentication failed"))
                .when(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body", NotificationType.EMPLOYEE_CREATED);

        notificationDeliveryService.handleIncomingMessage(request, NotificationType.EMPLOYEE_CREATED);

        ArgumentCaptor<NotificationDelivery> saveCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository, atLeastOnce()).save(saveCaptor.capture());
        NotificationDelivery finalSaved = saveCaptor.getValue();

        assertEquals(NotificationDeliveryStatus.FAILED, finalSaved.getStatus());
        assertEquals(1, finalSaved.getRetryCount());
        verify(retryTaskQueue, never()).schedule(any(), any());
    }

    @Test
    void handleIncomingMessagePersistsFailedAuditWhenPayloadIsInvalid() {
        notificationDeliveryService.handleIncomingMessage(null, NotificationType.EMPLOYEE_CREATED);

        ArgumentCaptor<NotificationDelivery> saveCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).save(saveCaptor.capture());
        NotificationDelivery saved = saveCaptor.getValue();

        assertEquals(NotificationDeliveryStatus.FAILED, saved.getStatus());
        assertEquals(NotificationType.EMPLOYEE_CREATED, saved.getNotificationType());
        assertEquals("unknown", saved.getRecipientEmail());
        assertEquals("Notification payload is required", saved.getLastError());
        verify(notificationService, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void handleIncomingMessagePersistsFailedAuditWhenRoutingKeyIsUnsupported() {
        NotificationRequest request = new NotificationRequest("Dimitrije", TEST_EMAIL, Map.of());

        notificationDeliveryService.handleIncomingMessage(request, "employee.unknown");

        ArgumentCaptor<NotificationDelivery> saveCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).save(saveCaptor.capture());
        NotificationDelivery saved = saveCaptor.getValue();

        assertEquals(NotificationDeliveryStatus.FAILED, saved.getStatus());
        assertEquals(NotificationType.UNKNOWN, saved.getNotificationType());
        assertEquals(TEST_EMAIL, saved.getRecipientEmail());
        assertEquals("Unsupported routing key: employee.unknown", saved.getLastError());
        verify(notificationService, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void processDueRetriesLoadsFromDbAndMarksSucceededWhenTaskIsDue() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("delivery-1", now.minusSeconds(1));
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setRecipientEmail(TEST_EMAIL);
        delivery.setSubject("Hello");
        delivery.setBody("Body");
        delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        delivery.setNotificationType(NotificationType.EMPLOYEE_CREATED);
        delivery.setRetryCount(1);
        delivery.setMaxRetries(4);
        delivery.setNextAttemptAt(now.minusSeconds(1));

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);
        when(notificationDeliveryRepository.findByDeliveryId("delivery-1")).thenReturn(Optional.of(delivery));

        notificationDeliveryService.processDueRetries();

        verify(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body", NotificationType.EMPLOYEE_CREATED);
        ArgumentCaptor<NotificationDelivery> saveCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository, atLeastOnce()).save(saveCaptor.capture());
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, saveCaptor.getValue().getStatus());
    }

    @Test
    void processDueRetriesDoesNothingWhenNextTaskIsNotDueYet() {
        Instant future = Instant.now().plusSeconds(30);
        when(retryTaskQueue.peek()).thenReturn(new RetryTask("delivery-future", future));

        notificationDeliveryService.processDueRetries();

        verify(retryTaskQueue, never()).pollDue(any(Instant.class));
        verify(notificationDeliveryRepository, never()).findByDeliveryId(any());
        verify(notificationService, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void processDueRetriesReschedulesTaskWhenDatabaseStateWasMovedIntoFuture() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("delivery-1", now.minusSeconds(1));
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setRecipientEmail(TEST_EMAIL);
        delivery.setSubject("Hello");
        delivery.setBody("Body");
        delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        delivery.setNotificationType(NotificationType.EMPLOYEE_CREATED);
        delivery.setRetryCount(1);
        delivery.setMaxRetries(4);
        delivery.setNextAttemptAt(now.plusSeconds(60));

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);
        when(notificationDeliveryRepository.findByDeliveryId("delivery-1")).thenReturn(Optional.of(delivery));

        notificationDeliveryService.processDueRetries();

        verify(retryTaskQueue).schedule("delivery-1", delivery.getNextAttemptAt());
        verify(notificationService, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void processDueRetriesMarksDeliveryFailedWhenRetryBudgetIsExhausted() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("delivery-1", now.minusSeconds(1));
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setRecipientEmail(TEST_EMAIL);
        delivery.setSubject("Hello");
        delivery.setBody("Body");
        delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        delivery.setNotificationType(NotificationType.EMPLOYEE_CREATED);
        delivery.setRetryCount(3);
        delivery.setMaxRetries(4);
        delivery.setNextAttemptAt(now.minusSeconds(1));

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);
        when(notificationDeliveryRepository.findByDeliveryId("delivery-1")).thenReturn(Optional.of(delivery));
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body", NotificationType.EMPLOYEE_CREATED);

        notificationDeliveryService.processDueRetries();

        ArgumentCaptor<NotificationDelivery> saveCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository, atLeastOnce()).save(saveCaptor.capture());
        NotificationDelivery finalSaved = saveCaptor.getValue();

        assertEquals(NotificationDeliveryStatus.FAILED, finalSaved.getStatus());
        assertEquals(4, finalSaved.getRetryCount());
        assertNull(finalSaved.getNextAttemptAt());
        verify(retryTaskQueue, never()).schedule("delivery-1", now.minusSeconds(1));
    }

    @Test
    void processDueRetriesSkipsMissingDeliveryRecord() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("missing-delivery", now.minusSeconds(1));

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);
        when(notificationDeliveryRepository.findByDeliveryId("missing-delivery")).thenReturn(Optional.empty());

        notificationDeliveryService.processDueRetries();

        verify(notificationService, never()).sendEmail(any(), any(), any(), any());
        verify(notificationDeliveryRepository, never()).save(any(NotificationDelivery.class));
        verify(notificationDeliveryRepository, times(1)).findByDeliveryId("missing-delivery");
    }

    @Test
    void loadRetryTasksOnStartupQueuesOnlyRetryableRecords() {
        NotificationDelivery retryable = new NotificationDelivery();
        retryable.setDeliveryId("delivery-retryable");
        retryable.setRetryCount(1);
        retryable.setMaxRetries(4);
        retryable.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        retryable.setNextAttemptAt(Instant.parse("2026-03-07T12:00:05Z"));

        NotificationDelivery exhausted = new NotificationDelivery();
        exhausted.setDeliveryId("delivery-exhausted");
        exhausted.setRetryCount(4);
        exhausted.setMaxRetries(4);
        exhausted.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        exhausted.setNextAttemptAt(Instant.parse("2026-03-07T12:00:10Z"));

        when(notificationDeliveryRepository.findAllByStatus(NotificationDeliveryStatus.RETRY_SCHEDULED))
                .thenReturn(List.of(retryable, exhausted));

        notificationDeliveryService.loadRetryTasksOnStartup();

        verify(retryTaskQueue).schedule("delivery-retryable", retryable.getNextAttemptAt());
        verify(retryTaskQueue, never()).schedule("delivery-exhausted", exhausted.getNextAttemptAt());
    }

}
