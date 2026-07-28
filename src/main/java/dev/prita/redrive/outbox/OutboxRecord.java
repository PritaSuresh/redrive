package dev.prita.redrive.outbox;

import dev.prita.redrive.ingest.EventRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "event_outbox")
public class OutboxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxRecord() {}

    public static OutboxRecord forEvent(EventRecord event, String topic) {
        var r = new OutboxRecord();
        r.eventId = event.getId();
        r.topic = topic;
        // Message key = event id. Ordering contract is deliberately weak in v1
        // (see ADR-0003); the key mainly spreads load across partitions
        // deterministically and enables per-event tracing.
        r.messageKey = event.getId().toString();
        r.payload = envelopeJson(event);
        r.published = false;
        r.publishAttempts = 0;
        r.createdAt = Instant.now();
        return r;
    }

    /** Minimal envelope so consumers don't need a DB read to route. */
    private static String envelopeJson(EventRecord e) {
        // payload is already valid JSON (stored as jsonb), so embed raw.
        return "{\"eventId\":\"" + e.getId() + "\",\"eventType\":\"" + e.getEventType()
                + "\",\"publisherId\":\"" + e.getPublisherId() + "\",\"createdAt\":\"" + e.getCreatedAt()
                + "\",\"payload\":" + e.getPayload() + "}";
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public boolean isPublished() { return published; }
    public int getPublishAttempts() { return publishAttempts; }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public void recordFailedAttempt() {
        this.publishAttempts++;
    }
}
