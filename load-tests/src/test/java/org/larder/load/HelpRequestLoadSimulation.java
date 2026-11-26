package org.larder.load;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.doIfOrElse;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * QS-03 (and QS-11 on a database seeded with 150,000 requests): 20 HTTP requests a second — 10
 * commands (5 ingredient, 5 catastrophe requests with two pictures), 10 queries — and 10 Grandma
 * Avatar answers a second on the broker, against the running service. Commands p95 at most 300 ms,
 * queries p95 at most 200 ms, no failed request, and no request left OPEN after the load (each of
 * Grandma's answers was published and consumed). Results are indicative: one CI runner, not
 * production.
 */
public class HelpRequestLoadSimulation extends Simulation {

  static final String TOMATO_SOUP = "3f0a6c1e-2b7d-4d9a-9c41-6a1f0e7b2c10";
  static final String CREME_FRAICHE = "8956b2e5-8d0c-470e-ae9c-6071bd7c5b0d";
  static final String ROAST_CHICKEN = "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e";
  static final String CREATE = "command: create an ingredient request";
  static final String CREATE_CATASTROPHE = "command: create a catastrophe request";
  static final String READ = "query: read a request";
  static final String LIST = "query: list requests";
  static final String LIST_RECENT = "query: list recent requests";
  static final String GRANDMA_ANSWERED = "check: Grandma answered every request";

  final int seconds = Integer.getInteger("durationSeconds", 120);
  final int commandsP95 = Integer.getInteger("commandsP95Ms", 300);
  final int queriesP95 = Integer.getInteger("queriesP95Ms", 200);

  /** Requests the commands created, for the queries and for Grandma's answers. */
  final List<String> created = new CopyOnWriteArrayList<>();

  final GrandmaAvatar grandma = new GrandmaAvatar(TOMATO_SOUP, CREME_FRAICHE, ROAST_CHICKEN);

  final HttpProtocolBuilder service =
      http.baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
          .header("Authorization", "Bearer " + System.getProperty("token"))
          .header("version", "1.0.15")
          .contentTypeHeader("application/json");

  final ScenarioBuilder commands =
      scenario("commands")
          .exec(
              http(CREATE)
                  .post("/help-requests")
                  .body(
                      StringBody(
                          """
                          {"title":"Crème fraîche in tomato soup","type":"INGREDIENT_SUBSTITUTE",
                           "description":"The shop has none","recipe":"%s","ingredient":"%s",
                           "ingredientName":"crème fraîche","substituteRequirements":"200 g",
                           "preferredProvider":["COMMUNITY","GRANDMA_AVATAR"]}"""
                              .formatted(TOMATO_SOUP, CREME_FRAICHE)))
                  .check(status().is(201), jsonPath("$.helpRequestId").saveAs("id")))
          .exec(
              session -> {
                created.add(session.getString("id"));
                grandma.willAnswer(session.getString("id"));
                return session;
              });

  final ScenarioBuilder catastropheCommands =
      scenario("catastrophe commands")
          .exec(
              http(CREATE_CATASTROPHE)
                  .post("/help-requests")
                  .body(
                      StringBody(
                          """
                          {"title":"My roast chicken is burnt","type":"STEPS_TO_MITIGATE_CATASTROPHE",
                           "description":"Burnt on the bottom","recipe":"%s",
                           "pictures":["https://img.larder.example/burnt-chicken-1.jpg",
                                       "https://img.larder.example/burnt-chicken-2.jpg"],
                           "preferredProvider":["COMMUNITY","GRANDMA_AVATAR"]}"""
                              .formatted(ROAST_CHICKEN)))
                  .check(status().is(201), jsonPath("$.helpRequestId").saveAs("id")))
          .exec(
              session -> {
                created.add(session.getString("id"));
                grandma.willMitigate(session.getString("id"));
                return session;
              });

  final HttpRequestActionBuilder listRequests =
      http(LIST).get("/help-requests?limit=20").check(status().is(200));

  final ScenarioBuilder queries =
      scenario("queries")
          .exec(
              session ->
                  session.set(
                      "id",
                      created.isEmpty()
                          ? ""
                          : created.get(ThreadLocalRandom.current().nextInt(created.size()))))
          .exec(
              doIfOrElse(session -> session.getString("id").isEmpty())
                  .then(exec(listRequests))
                  .orElse(
                      exec(
                          http(READ).get("/help-requests/#{id}").check(status().is(200)),
                          listRequests,
                          // a page of recent requests: Grandma answers every created one, so the
                          // recent ones are ANSWERED (an OPEN page would be nearly empty)
                          http(LIST_RECENT)
                              .get("/help-requests?status=ANSWERED&limit=20")
                              .check(status().is(200)))));

  /**
   * QS-03's 10 messages a second are served: after the load, no request is left OPEN, so each of
   * Grandma's answers was published and consumed. A failed request here fails the run (no failed
   * requests).
   */
  final ScenarioBuilder grandmasAnswers =
      scenario("Grandma's answers")
          .pause(Duration.ofSeconds(seconds + 5L))
          .exec(
              http(GRANDMA_ANSWERED)
                  .get("/help-requests?status=OPEN&limit=1")
                  .check(status().is(200), jsonPath("$.items[0]").notExists()));

  {
    // queries: 10/3 users a second, each reads one created request and lists twice: 10 queries a
    // second
    setUp(
            commands.injectOpen(constantUsersPerSec(5).during(seconds)),
            catastropheCommands.injectOpen(constantUsersPerSec(5).during(seconds)),
            queries.injectOpen(constantUsersPerSec(10.0 / 3).during(seconds)),
            grandmasAnswers.injectOpen(atOnceUsers(1)))
        .protocols(service)
        .assertions(
            global().failedRequests().count().is(0L),
            details(CREATE).responseTime().percentile(95.0).lte(commandsP95),
            details(CREATE_CATASTROPHE).responseTime().percentile(95.0).lte(commandsP95),
            details(READ).responseTime().percentile(95.0).lte(queriesP95),
            details(LIST).responseTime().percentile(95.0).lte(queriesP95),
            details(LIST_RECENT).responseTime().percentile(95.0).lte(queriesP95));
  }

  @Override
  public void before() {
    grandma.start();
  }

  @Override
  public void after() {
    grandma.stop();
  }
}
