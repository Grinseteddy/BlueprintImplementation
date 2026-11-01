package org.larder.cookingassistance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QS-02: module boundaries of the hexagonal layout as rules (ADR0008).
 */
class ArchitectureRulesTest {

  @Disabled("step 12 test list")
  @DisplayName("E1 domain depends on no framework, adapter or generated code; application only on the domain -- B3-S01,B3-S02 US-CA-03 QS-02 ADR0008")
  @Test
  void domainAndApplicationStayFreeOfFrameworksAndAdapters() {}
}
