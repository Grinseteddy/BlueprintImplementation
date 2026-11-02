package org.larder.cookingassistance.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC access to help_request rows and their child rows. */
interface HelpRequestRows extends CrudRepository<HelpRequestRow, UUID> {}
