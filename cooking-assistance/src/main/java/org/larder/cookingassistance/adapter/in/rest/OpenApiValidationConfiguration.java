package org.larder.cookingassistance.adapter.in.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the runtime enforcement of the approved OpenAPI spec on requests at the edge. */
@Configuration
class OpenApiValidationConfiguration implements WebMvcConfigurer {

  @Bean
  OpenApiContract openApiContract() {
    return new OpenApiContract();
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new OpenApiOptionsRefusal(openApiContract()));
    registry.addInterceptor(new OpenApiParameterValidation(openApiContract()));
  }
}
