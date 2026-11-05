package org.larder.cookingassistance.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Persistence model of a note (table help_request_note), a child row of help_request; {@code
 * helpId} names the rejected Help of a rejection note and is null for a cancellation note (INV-06,
 * INV-07).
 */
@Table("help_request_note")
record NoteRow(String kind, String text, Instant writtenAt, UUID helpId) {}
