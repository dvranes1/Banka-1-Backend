package app.service;

import app.dto.RetryTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link RetryTaskQueue}.
 */
class RetryTaskQueueUnitTest {

    @Test
    void queueReturnsEarliestTaskFirst() {
        RetryTaskQueue queue = new RetryTaskQueue();
        Instant base = Instant.parse("2026-03-07T12:00:00Z");

        queue.schedule("delivery-late", base.plusSeconds(10));
        queue.schedule("delivery-early", base.plusSeconds(5));

        RetryTask head = queue.peek();
        assertEquals("delivery-early", head.deliveryId());

        assertNull(queue.pollDue(base.plusSeconds(4)));
        assertEquals("delivery-early", queue.pollDue(base.plusSeconds(5)).deliveryId());
        assertEquals("delivery-late", queue.pollDue(base.plusSeconds(10)).deliveryId());
    }

    @Test
    void queueIgnoresStaleEntryWhenSameDeliveryIsRescheduled() {
        RetryTaskQueue queue = new RetryTaskQueue();
        Instant firstAttempt = Instant.parse("2026-03-07T12:00:05Z");
        Instant secondAttempt = Instant.parse("2026-03-07T12:00:10Z");

        queue.schedule("delivery-1", firstAttempt);
        queue.schedule("delivery-1", secondAttempt);

        assertNull(queue.pollDue(firstAttempt));
        assertEquals("delivery-1", queue.pollDue(secondAttempt).deliveryId());
    }
}
