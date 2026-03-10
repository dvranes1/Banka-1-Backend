package app.service;

import app.dto.NotificationRequest;
import app.dto.ResolvedEmail;
import app.dto.RetryTask;
import app.entities.NotificationDelivery;
import app.entities.NotificationDeliveryStatus;
import app.entities.NotificationType;
import app.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates incoming delivery processing and database-backed retry lifecycle.
 */
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {
    /** Maximum stored error message length. */
    private static final int MAX_ERROR_LENGTH = 1000;
    /** Placeholder recipient stored when the incoming payload does not contain one. */
    private static final String UNKNOWN_RECIPIENT = "unknown";
    /** Placeholder text stored when email content could not be rendered. */
    private static final String EMPTY_CONTENT = "";

    /**
     * Persistence layer for delivery state transitions.
     */
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    /**
     * Email renderer/sender abstraction.
     */
    private final NotificationService notificationService;
    /**
     * In-memory scheduler optimization for due retries.
     */
    private final RetryTaskQueue retryTaskQueue;

    /**
     * Configured retry budget per new delivery record.
     */
    @Value("${notification.retry.max-retries:4}")
    private int defaultMaxRetries;

    /**
     * Delay in seconds before a retryable failed delivery is attempted again.
     */
    @Value("${notification.retry.delay-seconds:5}")
    private int retryDelaySeconds;

    /**
     * Handles a newly consumed RabbitMQ message using the raw routing key.
     *
     * @param req incoming notification payload
     * @param routingKey routing key from RabbitMQ
     */
    public void handleIncomingMessage(NotificationRequest req, String routingKey) {
        Optional<NotificationType> notificationType = resolveNotificationType(routingKey);
        if (notificationType.isEmpty()) {
            persistFailedAudit(req, NotificationType.UNKNOWN, "Unsupported routing key: " + routingKey);
            return;
        }
        handleIncomingMessage(req, notificationType.get());
    }

    /**
     * Handles a consumed RabbitMQ message after the notification type is known.
     *
     * @param req incoming notification payload
     * @param notificationType type resolved from RabbitMQ routing key
     */
    public void handleIncomingMessage(NotificationRequest req, NotificationType notificationType) {
        try {
            validateIncoming(req);
            validateNotificationType(notificationType);
            ResolvedEmail resolvedEmail = notificationService.resolveEmailContent(req, notificationType);
            String deliveryId = UUID.randomUUID().toString();
            NotificationDelivery delivery = createNewDelivery(
                    deliveryId, resolvedEmail, normalizeNotificationType(notificationType)
            );
            attemptDelivery(delivery);
        } catch (RuntimeException ex) {
            persistFailedAudit(req, normalizeNotificationType(notificationType), trimError(ex));
        }
    }

    /**
     * Scheduled retry worker that only inspects the queue head and processes due tasks.
     */
    @Scheduled(fixedDelayString = "${notification.retry.scheduler-delay-millis:1000}")
    public void processDueRetries() {
        while (true) {
            RetryTask head = retryTaskQueue.peek();
            Instant now = Instant.now();
            if (head == null || head.nextAttemptAt().isAfter(now)) {
                return;
            }

            RetryTask dueTask = retryTaskQueue.pollDue(now);
            if (dueTask == null) {
                return;
            }
            processRetryTask(dueTask.deliveryId());
        }
    }

    /**
     * Reloads retryable deliveries from PostgreSQL into the in-memory queue on startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void loadRetryTasksOnStartup() {
        List<NotificationDelivery> deliveries = notificationDeliveryRepository
                .findAllByStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        for (NotificationDelivery delivery : deliveries) {
            if (delivery.getRetryCount() < delivery.getMaxRetries()
                    && delivery.getNextAttemptAt() != null) {
                retryTaskQueue.schedule(delivery.getDeliveryId(), delivery.getNextAttemptAt());
            }
        }
    }

    /**
     * Executes one retry task after validating the latest database state.
     *
     * @param deliveryId internal delivery identifier
     */
    private void processRetryTask(String deliveryId) {
        Optional<NotificationDelivery> optionalDelivery = notificationDeliveryRepository
                .findByDeliveryId(deliveryId);
        if (optionalDelivery.isEmpty()) {
            return;
        }

        NotificationDelivery delivery = optionalDelivery.get();
        if (delivery.getStatus() != NotificationDeliveryStatus.RETRY_SCHEDULED) {
            return;
        }
        if (delivery.getNextAttemptAt() == null) {
            return;
        }
        if (delivery.getNextAttemptAt().isAfter(Instant.now())) {
            retryTaskQueue.schedule(delivery.getDeliveryId(), delivery.getNextAttemptAt());
            return;
        }
        attemptDelivery(delivery);
    }

    /**
     * Creates a new persisted delivery record.
     *
     * @param deliveryId generated internal UUID
     * @param resolvedEmail rendered email content
     * @param notificationType resolved notification type
     * @return persisted delivery record
     */
    private NotificationDelivery createNewDelivery(
            String deliveryId,
            ResolvedEmail resolvedEmail,
            NotificationType notificationType
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId(deliveryId);
        delivery.setRetryCount(0);
        delivery.setMaxRetries(defaultMaxRetries);
        delivery.setStatus(NotificationDeliveryStatus.PENDING);
        delivery.setNotificationType(notificationType);
        updateDeliveryPayload(delivery, resolvedEmail);
        return notificationDeliveryRepository.save(delivery);
    }

    /**
     * Persists a terminally failed record for invalid or unsupported incoming messages.
     *
     * @param request incoming payload, which may be null or malformed
     * @param notificationType resolved or fallback notification type
     * @param error error reason stored with the delivery
     */
    private void persistFailedAudit(
            NotificationRequest request,
            NotificationType notificationType,
            String error
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId(UUID.randomUUID().toString());
        delivery.setRetryCount(0);
        delivery.setMaxRetries(defaultMaxRetries);
        delivery.setStatus(NotificationDeliveryStatus.FAILED);
        delivery.setNotificationType(notificationType);
        delivery.setRecipientEmail(extractUserEmail(request));
        delivery.setSubject(EMPTY_CONTENT);
        delivery.setBody(EMPTY_CONTENT);
        delivery.setLastError(error);
        delivery.setNextAttemptAt(null);
        delivery.setLastAttemptAt(null);
        delivery.setSentAt(null);
        notificationDeliveryRepository.save(delivery);
    }

    /**
     * Copies rendered email fields into a mutable delivery entity.
     *
     * @param delivery target entity
     * @param resolvedEmail source email content
     */
    private void updateDeliveryPayload(NotificationDelivery delivery, ResolvedEmail resolvedEmail) {
        delivery.setRecipientEmail(resolvedEmail.recipientEmail());
        delivery.setSubject(resolvedEmail.subject());
        delivery.setBody(resolvedEmail.body());
    }

    /**
     * Performs one immediate send attempt and updates persistence state accordingly.
     *
     * @param delivery mutable delivery entity
     */
    private void attemptDelivery(NotificationDelivery delivery) {
        Instant now = Instant.now();
        delivery.setStatus(NotificationDeliveryStatus.PROCESSING);
        delivery.setLastAttemptAt(now);
        delivery.setNextAttemptAt(null);
        notificationDeliveryRepository.save(delivery);

        try {
            notificationService.sendEmail(
                    delivery.getRecipientEmail(),
                    delivery.getSubject(),
                    delivery.getBody(),
                    delivery.getNotificationType()
            );
            markSucceeded(delivery, now);
        } catch (Exception ex) {
            markFailedOrRetry(delivery, now, ex);
        }
    }

    /**
     * Marks delivery as successful and stores completion metadata.
     *
     * @param delivery delivery entity
     * @param sentAt send completion timestamp
     */
    private void markSucceeded(NotificationDelivery delivery, Instant sentAt) {
        delivery.setStatus(NotificationDeliveryStatus.SUCCEEDED);
        delivery.setSentAt(sentAt);
        delivery.setLastError(null);
        delivery.setNextAttemptAt(null);
        notificationDeliveryRepository.save(delivery);
    }

    /**
     * Handles send failure by scheduling a retry or marking terminal failure.
     *
     * @param delivery delivery entity
     * @param now failure timestamp
     * @param ex thrown email send exception
     */
    private void markFailedOrRetry(NotificationDelivery delivery, Instant now, Exception ex) {
        int updatedRetryCount = delivery.getRetryCount() + 1;
        delivery.setRetryCount(updatedRetryCount);
        delivery.setLastError(trimError(ex));
        boolean retryableFailure = isRetryable(ex);

        if (retryableFailure && updatedRetryCount < delivery.getMaxRetries()) {
            Instant nextAttemptAt = now.plusSeconds(retryDelaySeconds);
            delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
            delivery.setNextAttemptAt(nextAttemptAt);
            notificationDeliveryRepository.save(delivery);
            retryTaskQueue.schedule(delivery.getDeliveryId(), nextAttemptAt);
            return;
        }

        delivery.setStatus(NotificationDeliveryStatus.FAILED);
        delivery.setNextAttemptAt(null);
        notificationDeliveryRepository.save(delivery);
    }

    /**
     * Maps RabbitMQ routing keys to supported notification event types.
     *
     * @param routingKey routing key from AMQP message headers
     * @return resolved event type when supported
     */
    private Optional<NotificationType> resolveNotificationType(String routingKey) {
        return switch (routingKey) {
            case "employee.created" -> Optional.of(NotificationType.EMPLOYEE_CREATED);
            case "employee.password_reset" -> Optional.of(NotificationType.EMPLOYEE_PASSWORD_RESET);
            case "employee.account_deactivated" -> Optional.of(NotificationType.EMPLOYEE_ACCOUNT_DEACTIVATED);
            default -> Optional.empty();
        };
    }

    /**
     * Validates basic incoming payload shape.
     *
     * @param request payload object from listener
     */
    private void validateIncoming(NotificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Notification payload is required");
        }
    }

    /**
     * Validates required notification type resolved from routing key.
     *
     * @param notificationType resolved notification type
     */
    private void validateNotificationType(NotificationType notificationType) {
        if (notificationType == null) {
            throw new IllegalArgumentException("notificationType is required");
        }
    }

    /**
     * Produces a bounded string representation for persistence.
     *
     * @param ex source exception
     * @return trimmed error text up to 1000 characters
     */
    private String trimError(Exception ex) {
        String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        if (error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }

    private boolean isRetryable(Exception ex) {
        return !(ex instanceof MailAuthenticationException);
    }

    private NotificationType normalizeNotificationType(NotificationType notificationType) {
        return notificationType == null ? NotificationType.UNKNOWN : notificationType;
    }

    private String extractUserEmail(NotificationRequest request) {
        if (request == null || request.getUserEmail() == null || request.getUserEmail().isBlank()) {
            return UNKNOWN_RECIPIENT;
        }
        return request.getUserEmail();
    }
}
