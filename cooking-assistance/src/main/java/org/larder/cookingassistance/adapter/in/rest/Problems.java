package org.larder.cookingassistance.adapter.in.rest;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Problem Details (RFC 9457) with the API's domain code; the type is the code in kebab case. */
final class Problems {

  private static final String CODE = "code";

  private Problems() {}

  static ProblemDetail of(HttpStatus status, String code, String title) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(
        URI.create(
            "https://larder.org/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    problem.setTitle(title);
    problem.setProperty(CODE, code);
    return problem;
  }

  /** Whether the Problem already carries the API's domain code. */
  static boolean carriesCode(ProblemDetail problem) {
    return problem.getProperties() != null && problem.getProperties().containsKey(CODE);
  }
}
