package nexus.io.agent.demo.utils;

import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.utils.validator.ParameterValidator;

/** Request parameter checks; failures become HTTP 400 in the global exception handler. */
public final class ParameterValidationUtils {

  private ParameterValidationUtils() {
  }

  /** The request body as text, required and at most {@code maxBytes} long. */
  public static String requestBody(HttpRequest request, int maxBytes) {
    String body = request.getBodyString();
    ParameterValidator.require(body != null && !body.trim().isEmpty(), "Request body is required");
    ParameterValidator.require(body.length() <= maxBytes, "Request body is too large");
    return body;
  }

  public static void required(Object value, String message) {
    ParameterValidator.require(value != null, message);
  }

  public static String text(Object raw, String name, int max, boolean required) {
    return ParameterValidator.text(raw, name, max, required);
  }

  public static String choice(Object raw, String name, String fallback, String... options) {
    return ParameterValidator.choice(raw, name, fallback, options);
  }

  /** A positive snowflake id from a path parameter. */
  public static long id(HttpRequest request, String name) {
    return ParameterValidator.id(request.getParam(name), name);
  }
}
