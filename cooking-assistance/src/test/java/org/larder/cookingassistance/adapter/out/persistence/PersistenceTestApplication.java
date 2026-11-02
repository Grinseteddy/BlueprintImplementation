package org.larder.cookingassistance.adapter.out.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Boots only the Cook Assistance module for persistence integration tests. */
@SpringBootApplication(scanBasePackages = "org.larder.cookingassistance")
public class PersistenceTestApplication {}
