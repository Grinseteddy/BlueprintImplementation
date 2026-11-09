package org.larder.cookingassistance.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import org.larder.cookingassistance.application.HelpFilter;
import org.larder.cookingassistance.application.HelpRequestFilter;
import org.larder.cookingassistance.application.HelpRequestQueries;
import org.larder.cookingassistance.application.HelpRequestView;
import org.larder.cookingassistance.application.HelpView;
import org.larder.cookingassistance.application.Page;
import org.larder.cookingassistance.application.PreviousNote;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.Note;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads help requests from the aggregate's tables (ADR0016: read-your-writes, QS-07); child rows
 * become values through {@link HelpRequestMapper}, as on the write side.
 */
@Repository
class JdbcHelpRequestQueries implements HelpRequestQueries {

  // SEC-14: a list page stays bounded however many requests a Cook makes on one step
  private static final int PREVIOUS_NOTES_SHOWN = 10;

  /** A Help's row with its request's requester and the rejection note that names it. */
  private static final String HELPS =
      "select h.id, h.help_request_id, h.provider_type, h.provider_person, h.answer_title,"
          + " h.answer, h.accepted, h.rejected, r.requester, n.text as rejection_note"
          + " from help h join help_request r on r.id = h.help_request_id"
          + " left join help_request_note n on n.help_id = h.id and n.kind = 'REJECTION'";

  /**
   * INV-07: the notes of the requester's earlier requests (UUIDv7: a smaller id was created
   * earlier) on the same step of the same recipe as the request read.
   */
  private static final String NOTES_OF_EARLIER_REQUESTS_ON_THE_STEP =
      "select n.help_request_id, n.position, n.kind, n.text, n.written_at, n.help_id"
          + " from help_request_note n"
          + " join help_request earlier on earlier.id = n.help_request_id"
          + " join help_request read on read.id = :id"
          + " where earlier.id < read.id"
          + " and earlier.requester = read.requester"
          + " and earlier.recipe = read.recipe"
          + " and earlier.how_to_step = read.how_to_step";

  private final JdbcClient jdbc;
  private final TransactionTemplate snapshot;

  JdbcHelpRequestQueries(JdbcClient jdbc, PlatformTransactionManager transactions) {
    this.jdbc = jdbc;
    this.snapshot = oneSnapshot(transactions);
  }

  /** ADR0016: everything one view shows is read from one snapshot, never torn. */
  private static TransactionTemplate oneSnapshot(PlatformTransactionManager transactions) {
    TransactionTemplate snapshot = new TransactionTemplate(transactions);
    snapshot.setReadOnly(true);
    snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return snapshot;
  }

  @Override
  public Optional<HelpRequestView> byId(HelpRequestId id) {
    return snapshot.execute(transaction -> readById(id));
  }

  @Override
  public Page<HelpRequestView> list(
      HelpRequestFilter filter, int limit, Optional<HelpRequestId> after) {
    return snapshot.execute(
        transaction -> {
          Page<HelpRequestId> ids = keysetPage(limit, count -> idsAfter(filter, after, count));
          return new Page<>(
              ids.items().stream().map(id -> readById(id).orElseThrow()).toList(), ids.hasMore());
        });
  }

  /** ADR0016 L6: one row more than the page tells whether a next page exists. */
  private static <T> Page<T> keysetPage(int limit, IntFunction<List<T>> fetch) {
    List<T> fetched = fetch.apply(limit + 1);
    return new Page<>(fetched.stream().limit(limit).toList(), fetched.size() > limit);
  }

  /** ADR0016 L6: keyset paging, the ids the filter lets through after the cursor, oldest first. */
  private List<HelpRequestId> idsAfter(
      HelpRequestFilter filter, Optional<HelpRequestId> after, int count) {
    return jdbc.sql(
            "select id from help_request"
                + " where (cast(:requester as uuid) is null or requester = :requester)"
                + " and (cast(:status as text) is null or status = :status)"
                + " and (cast(:type as text) is null or type = :type)"
                + " and (cast(:after as uuid) is null or id > :after)"
                + " order by id limit :count")
        .param("requester", filter.requester().map(CookId::value).orElse(null))
        .param("status", filter.status().map(Enum::name).orElse(null))
        .param("type", filter.type().map(Enum::name).orElse(null))
        .param("after", after.map(HelpRequestId::value).orElse(null))
        .param("count", count)
        .query((rs, n) -> new HelpRequestId(rs.getObject("id", UUID.class)))
        .list();
  }

  @Override
  public Optional<Page<HelpView>> helpsOf(HelpRequestId id, int limit, Optional<HelpId> after) {
    return snapshot.execute(
        transaction -> {
          if (!exists(id)) {
            return Optional.empty();
          }
          return Optional.of(keysetPage(limit, count -> helpsAfter(id, after, count)));
        });
  }

  /** ADR0016 L6: keyset paging, the Helps of the request after the cursor, oldest first. */
  private List<HelpView> helpsAfter(HelpRequestId id, Optional<HelpId> after, int count) {
    return jdbc.sql(
            HELPS
                + " where h.help_request_id = :id"
                + " and (cast(:after as uuid) is null or h.id > :after)"
                + " order by h.id limit :count")
        .param("id", id.value())
        .param("after", after.map(HelpId::value).orElse(null))
        .param("count", count)
        .query((rs, n) -> helpView(rs))
        .list();
  }

  @Override
  public Optional<HelpView> helpOf(HelpRequestId request, HelpId help) {
    return snapshot.execute(
        transaction ->
            jdbc.sql(HELPS + " where h.help_request_id = :id and h.id = :help")
                .param("id", request.value())
                .param("help", help.value())
                .query((rs, n) -> helpView(rs))
                .optional());
  }

  @Override
  public Page<HelpView> helps(HelpFilter filter, int limit, Optional<HelpId> after) {
    return snapshot.execute(
        transaction -> keysetPage(limit, count -> helpsAfter(filter, after, count)));
  }

  /** ADR0016 L6: keyset paging, the filtered Helps after the cursor, oldest first. */
  private List<HelpView> helpsAfter(HelpFilter filter, Optional<HelpId> after, int count) {
    return jdbc.sql(
            HELPS
                + " where (cast(:provider as uuid) is null or h.provider_person = :provider)"
                // INV-37: an answer has the type of its request
                + " and (cast(:type as text) is null or r.type = :type)"
                + " and (cast(:after as uuid) is null or h.id > :after)"
                + " order by h.id limit :count")
        .param("provider", filter.helpProvider().map(CookId::value).orElse(null))
        .param("type", filter.type().map(Enum::name).orElse(null))
        .param("after", after.map(HelpId::value).orElse(null))
        .param("count", count)
        .query((rs, n) -> helpView(rs))
        .list();
  }

  private static HelpView helpView(ResultSet rs) throws SQLException {
    Help help =
        HelpRequestMapper.toDomain(
            new HelpRow(
                rs.getObject("id", UUID.class),
                rs.getString("provider_type"),
                rs.getObject("provider_person", UUID.class),
                rs.getString("answer_title"),
                new AnswerJson(rs.getString("answer")),
                rs.getBoolean("accepted"),
                rs.getBoolean("rejected")));
    return new HelpView(
        help.id(),
        new HelpRequestId(rs.getObject("help_request_id", UUID.class)),
        new CookId(rs.getObject("requester", UUID.class)),
        help.answerTitle(),
        new HelpProvider(help.providerType(), help.provider()),
        // only preparation step explanations are stored so far (step 7 of increment 2)
        (PreparationStepExplanation) help.answer(),
        help.isAccepted(),
        Optional.ofNullable(rs.getString("rejection_note")));
  }

  private boolean exists(HelpRequestId id) {
    return jdbc.sql("select exists (select 1 from help_request where id = :id)")
        .param("id", id.value())
        .query(Boolean.class)
        .single();
  }

  private Optional<HelpRequestView> readById(HelpRequestId id) {
    List<AskedProvider> asked = askedProviders(id);
    List<Note> notes = notes(id);
    List<PreviousNote> previousNotes = previousNotes(id);
    Optional<HelpId> acceptedHelp = acceptedHelp(id);
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
                    HelpRequestStatus.valueOf(rs.getString("status")),
                    notes,
                    previousNotes,
                    acceptedHelp))
        .optional();
  }

  private List<AskedProvider> askedProviders(HelpRequestId id) {
    return jdbc.sql(
            "select provider_type, deadline, state from asked_provider"
                + " where help_request_id = :id order by position")
        .param("id", id.value())
        .query(
            (rs, n) ->
                HelpRequestMapper.toDomain(
                    new AskedProviderRow(
                        rs.getString("provider_type"),
                        rs.getObject("deadline", OffsetDateTime.class).toInstant(),
                        rs.getString("state"))))
        .list();
  }

  private List<Note> notes(HelpRequestId id) {
    return jdbc.sql(
            "select kind, text, written_at, help_id from help_request_note"
                + " where help_request_id = :id order by position")
        .param("id", id.value())
        .query((rs, n) -> note(rs))
        .list();
  }

  private Optional<HelpId> acceptedHelp(HelpRequestId id) {
    return jdbc.sql("select id from help where help_request_id = :id and accepted")
        .param("id", id.value())
        .query((rs, n) -> new HelpId(rs.getObject("id", UUID.class)))
        .optional();
  }

  /** SEC-14: of the notes of earlier requests only the most recent are shown, oldest first. */
  private List<PreviousNote> previousNotes(HelpRequestId id) {
    return jdbc.sql(
            "select * from ("
                + NOTES_OF_EARLIER_REQUESTS_ON_THE_STEP
                + " order by n.help_request_id desc, n.position desc"
                + " limit :shown"
                + ") recent order by help_request_id, position")
        .param("id", id.value())
        .param("shown", PREVIOUS_NOTES_SHOWN)
        .query(
            (rs, n) ->
                new PreviousNote(
                    note(rs), new HelpRequestId(rs.getObject("help_request_id", UUID.class))))
        .list();
  }

  private static Note note(ResultSet rs) throws SQLException {
    return HelpRequestMapper.toDomain(
        new NoteRow(
            rs.getString("kind"),
            rs.getString("text"),
            rs.getObject("written_at", OffsetDateTime.class).toInstant(),
            rs.getObject("help_id", UUID.class)));
  }
}
