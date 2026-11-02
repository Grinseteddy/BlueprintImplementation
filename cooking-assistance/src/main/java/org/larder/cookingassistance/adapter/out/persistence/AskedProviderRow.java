package org.larder.cookingassistance.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.relational.core.mapping.Table;

/** Persistence model of an asked provider (table asked_provider), a child row of help_request. */
@Table("asked_provider")
record AskedProviderRow(String providerType, Instant deadline, String state) {}
