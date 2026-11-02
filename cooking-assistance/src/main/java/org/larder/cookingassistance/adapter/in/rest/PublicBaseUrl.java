package org.larder.cookingassistance.adapter.in.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Value;

/**
 * Injects the configured public base URL that every link in a response starts with, never the
 * request's Host (security review SEC-10).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Value("${cooking-assistance.public-base-url}")
@interface PublicBaseUrl {}
