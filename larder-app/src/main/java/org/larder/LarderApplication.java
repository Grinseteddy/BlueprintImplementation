package org.larder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The Larder monolith: one process composing its bounded-context modules (ADR0001, ADR0007). */
@SpringBootApplication
public class LarderApplication {

  public static void main(String[] args) {
    SpringApplication.run(LarderApplication.class, args);
  }
}
