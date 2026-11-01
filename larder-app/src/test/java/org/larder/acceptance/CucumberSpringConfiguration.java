package org.larder.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.larder.LarderApplication;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The running Larder service on a random port, on real PostgreSQL and RabbitMQ. */
@CucumberContextConfiguration
@SpringBootTest(classes = LarderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
public class CucumberSpringConfiguration {}
