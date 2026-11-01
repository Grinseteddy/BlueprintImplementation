package org.larder.cookingassistance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QS-02: module boundaries of the hexagonal layout as rules (ADR0008). */
class ArchitectureRulesTest {

  static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("org.larder.cookingassistance");

  @DisplayName(
      "E1 domain depends on no framework, adapter or generated code; application only on the domain -- B3-S01,B3-S02 US-CA-03 QS-02 ADR0008")
  @Test
  void domainAndApplicationStayFreeOfFrameworksAndAdapters() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..", "jakarta..", "com.fasterxml..", "..adapter..", "..generated..")
        .check(PRODUCTION);
    classes()
        .that()
        .resideInAPackage("..application..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("..domain..", "..application..", "java..")
        .check(PRODUCTION);
  }
}
