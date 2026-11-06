package org.larder;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Errors Tomcat answers before Spring sees the request (a header line breaking RFC 9112, say) are
 * Problem Details too, never Tomcat's HTML page (step 12, Schemathesis).
 */
@Configuration(proxyBeanMethods = false)
class ProblemErrorReports {

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> problemErrorReportValve() {
    return factory ->
        factory.addContextCustomizers(
            context ->
                ((StandardHost) context.getParent())
                    .setErrorReportValveClass(ProblemErrorReportValve.class.getName()));
  }

  /** Writes an error without a body as application/problem+json, like the module's Problems. */
  public static class ProblemErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
      int status = response.getStatus();
      if (status < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
        return;
      }
      try {
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Writer writer = response.getReporter();
        if (writer != null) {
          writer.write(problem(status));
        }
      } catch (IOException | IllegalStateException e) {
        // the response is already gone; nothing more to report
      }
    }

    /** The error map's entry for a status the container answers itself. */
    private static String problem(int status) {
      if (status == 400) {
        return body("VALIDATION_FAILED", "The request does not match the API contract", status);
      }
      if (status >= 500) {
        return body("INTERNAL_ERROR", "Unexpected error", status);
      }
      HttpStatus known = HttpStatus.resolve(status);
      return known == null
          ? body("ERROR", "Error", status)
          : body(known.name(), known.getReasonPhrase(), status);
    }

    /** The Problem as JSON; the type is the code in kebab case, as the module writes it. */
    private static String body(String code, String title, int status) {
      String type = code.toLowerCase(Locale.ROOT).replace('_', '-');
      return "{\"type\":\"https://larder.org/problems/%s\",\"title\":\"%s\",\"status\":%d,\"code\":\"%s\"}"
          .formatted(type, title, status, code);
    }
  }
}
