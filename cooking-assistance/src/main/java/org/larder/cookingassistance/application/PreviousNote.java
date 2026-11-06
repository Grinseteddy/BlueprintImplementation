package org.larder.cookingassistance.application;

import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.Note;

/**
 * A note of one of the requester's earlier requests, with the request it was written on (INV-07).
 */
public record PreviousNote(Note note, HelpRequestId helpRequest) {}
