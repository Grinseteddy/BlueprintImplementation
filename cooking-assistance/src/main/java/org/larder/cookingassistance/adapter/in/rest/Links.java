package org.larder.cookingassistance.adapter.in.rest;

import java.net.URI;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Links in responses are absolute, built from the request's base URL. */
final class Links {

  private Links() {}

  static URI absolute(String pathTemplate, Object... values) {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path(pathTemplate)
        .buildAndExpand(values)
        .toUri();
  }
}
