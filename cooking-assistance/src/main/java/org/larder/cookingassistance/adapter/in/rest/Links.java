package org.larder.cookingassistance.adapter.in.rest;

import java.net.URI;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Links in responses are absolute, built from the configured public base URL, never from the
 * request's Host (security review SEC-10).
 */
final class Links {

  private Links() {}

  static URI absolute(String publicBaseUrl, String pathTemplate, Object... values) {
    return UriComponentsBuilder.fromUriString(publicBaseUrl)
        .path(pathTemplate)
        .buildAndExpand(values)
        .toUri();
  }
}
