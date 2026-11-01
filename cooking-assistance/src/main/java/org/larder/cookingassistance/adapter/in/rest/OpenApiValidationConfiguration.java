package org.larder.cookingassistance.adapter.in.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the runtime validation of request parameters against the approved OpenAPI spec. */
@Configuration
class OpenApiValidationConfiguration implements WebMvcConfigurer {

  @Bean
  OpenApiContract openApiContract() {
    return new OpenApiContract();
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new OpenApiParameterValidation(openApiContract()));
  }
}
