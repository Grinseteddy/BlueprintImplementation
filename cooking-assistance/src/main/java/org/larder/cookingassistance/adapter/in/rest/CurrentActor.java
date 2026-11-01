package org.larder.cookingassistance.adapter.in.rest;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.larder.cookingassistance.application.Actor;
import org.larder.cookingassistance.application.Role;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller as an Actor (ADR0012): sub is the cook id, roles and premium are claims.
 */
final class CurrentActor {

  private CurrentActor() {}

  static Actor get() {
    Jwt token = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    // a token without the roles claim carries no role
    List<String> roles = Objects.requireNonNullElse(token.getClaimAsStringList("roles"), List.of());
    return new Actor(
        new CookId(UUID.fromString(token.getSubject())),
        // ADR0013, deny by default: a role this module does not know grants nothing
        Arrays.stream(Role.values())
            .filter(role -> roles.contains(role.name().toLowerCase(Locale.ROOT)))
            .collect(Collectors.toSet()),
        Boolean.TRUE.equals(token.getClaimAsBoolean("premium")));
  }
}
