package org.larder.cookingassistance.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.larder.cookingassistance.application.HelpRequestQueries;
import org.larder.cookingassistance.application.HelpRequestView;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads help requests from the aggregate's tables (ADR0016: read-your-writes, QS-07). */
@Repository
class JdbcHelpRequestQueries implements HelpRequestQueries {

  private final JdbcClient jdbc;

  JdbcHelpRequestQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<HelpRequestView> byId(HelpRequestId id) {
    List<AskedProvider> asked = askedProviders(id);
    return jdbc.sql(
            "select requester, title, type, description, recipe, how_to_step, status"
                + " from help_request where id = :id")
        .param("id", id.value())
        .query(
            (rs, n) ->
                new HelpRequestView(
                    id,
                    new CookId(rs.getObject("requester", UUID.class)),
                    rs.getString("title"),
                    HelpType.valueOf(rs.getString("type")),
                    rs.getString("description"),
                    new RecipeId(rs.getObject("recipe", UUID.class)),
                    new HowToStepId(rs.getObject("how_to_step", UUID.class)),
                    asked,
                    HelpRequestStatus.valueOf(rs.getString("status"))))
        .optional();
  }

  private List<AskedProvider> askedProviders(HelpRequestId id) {
    return jdbc.sql(
            "select provider_type, deadline, state from asked_provider"
                + " where help_request_id = :id order by position")
        .param("id", id.value())
        .query(
            (rs, n) ->
                new AskedProvider(
                    HelpProviderType.valueOf(rs.getString("provider_type")),
                    rs.getObject("deadline", OffsetDateTime.class).toInstant(),
                    ProviderState.valueOf(rs.getString("state"))))
        .list();
  }
}
