package org.larder.cookingassistance.adapter.in.rest;

import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpType;

/**
 * The terms the Help Requests and Helps resources share (components HelpType and HelpProviderType
 * of cooking-assistance.openapi.yaml): the contract's constants carry the domain's names.
 */
final class HelpVocabulary {

  private HelpVocabulary() {}

  static HelpType toDomain(
      org.larder.cookingassistance.adapter.in.rest.generated.model.HelpType type) {
    return HelpType.valueOf(type.name());
  }

  static org.larder.cookingassistance.adapter.in.rest.generated.model.HelpType toContract(
      HelpType type) {
    return org.larder.cookingassistance.adapter.in.rest.generated.model.HelpType.fromValue(
        type.name());
  }

  static HelpProviderType toDomain(
      org.larder.cookingassistance.adapter.in.rest.generated.model.HelpProviderType type) {
    return HelpProviderType.valueOf(type.name());
  }

  static org.larder.cookingassistance.adapter.in.rest.generated.model.HelpProviderType toContract(
      HelpProviderType type) {
    return org.larder.cookingassistance.adapter.in.rest.generated.model.HelpProviderType.fromValue(
        type.name());
  }
}
