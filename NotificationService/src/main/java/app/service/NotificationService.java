package app.service;

import app.dto.NotificationRequest;
import app.dto.ResolvedEmail;
import app.entities.NotificationType;
import app.template.NotificationTemplateFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Main service for rendering and sending email notifications.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    /** SMTP sender abstraction used to dispatch the final email. */
    private final JavaMailSender mailSender;

    /** Template provider used to resolve subject/body by notification type. */
    private final NotificationTemplateFactory templateFactory;

    /** Configured sender address; if blank the framework default is used. */
    @Value("${spring.mail.username:}")
    private String fromAddress = "";

    /**
     * Sends a notification email for a resolved event type.
     * @param request payload from the broker containing recipient and template data
     * @param type event type resolved from the routing key and used for template selection
     */
    public void sendNotification(NotificationRequest request, NotificationType type) {
        // Rendered recipient/subject/body used for delivery.
        ResolvedEmail resolvedEmail = resolveEmailContent(request, type);
        sendEmail(
                resolvedEmail.recipientEmail(),
                resolvedEmail.subject(),
                resolvedEmail.body(),
                type
        );
    }

    /**
     * Resolves the final rendered email content from request variables and templates.
     * @param request payload from the broker containing recipient and template values
     * @param type event type used to resolve the concrete email template
     * @return resolved email payload for SMTP delivery
     */
    public ResolvedEmail resolveEmailContent(NotificationRequest request, NotificationType type) {
        return NotificationContentResolver.resolve(request, type, templateFactory);
    }

    /**
     * Sends an email without notification type context.
     * @param to recipient email address
     * @param subject email subject line
     * @param content rendered email body
     */
    public void sendEmail(String to, String subject, String content) {
        sendEmail(to, subject, content, null);
    }

    /**
     * Sends an email with notification type context for audit logs.
     * @param to recipient email address
     * @param subject email subject line
     * @param content rendered email body
     * @param type notification type included in audit logs
     */
    public void sendEmail(String to, String subject, String content, NotificationType type) {
        sendMailMessage(to, subject, content);
    }

    /**
     * Builds and sends a {@link SimpleMailMessage}.
     * @param to recipient email address
     * @param subject email subject line
     * @param content rendered email body
     */
    private void sendMailMessage(String to, String subject, String content) {
        // Mutable Spring message object filled before SMTP dispatch.
        SimpleMailMessage message = new SimpleMailMessage();
        if (hasCustomFromAddress()) {
            message.setFrom(fromAddress);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    /** @return {@code true} when the configured sender should be applied */
    private boolean hasCustomFromAddress() {
        return fromAddress != null && !fromAddress.isBlank();
    }
}
