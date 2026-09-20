package com.swyp;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

final class Nessie {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  private final String base;
  private final String key;

  Nessie() {
    this(
        System.getenv().getOrDefault("NESSIE_BASE_URL", "https://prod-api.nessieisreal.com"),
        System.getenv("NESSIE_API_KEY"));
  }

  Nessie(String base, String key) {
    this.base = base;
    this.key = key;
  }

  Map<String, Object> call(String method, String path, Object body) throws Exception {
    if (key == null || key.isBlank())
      throw new IllegalStateException("Configure NESSIE_API_KEY on the backend");
    var req =
        HttpRequest.newBuilder(
                URI.create(
                    base
                        + path
                        + "?key="
                        + URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(Json.write(body)))
            .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2)
      throw new IllegalStateException(
          "Nessie rejected " + method + " " + path + " (HTTP " + res.statusCode() + ")");
    if (res.body().isBlank()) return Map.of();
    Object value;
    try {
      value = Json.M.readValue(res.body(), Object.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return Map.of("acknowledgement", res.body());
    }
    if (value instanceof Map<?, ?>) return Json.map(res.body());
    return Map.of("results", value);
  }

  String create(String path, Object body) throws Exception {
    var response = call("POST", path, body);
    String direct = createdId(response);
    if (direct != null) return direct;
    // Current docs also permit text acknowledgements. Resolve exactly one matching object
    // with a GET; never repeat a POST whose outcome may already have succeeded.
    if (!(body instanceof Map<?, ?> input))
      throw new IllegalStateException("Unknown Nessie creation response");
    var listed = call("GET", path, null).get("results");
    if (listed instanceof List<?> rows) {
      var matches = new ArrayList<String>();
      for (Object row : rows)
        if (row instanceof Map<?, ?> m) {
          boolean same = true;
          for (String key : List.of("first_name", "last_name", "nickname", "name", "description"))
            if (input.containsKey(key) && !Objects.equals(input.get(key), m.get(key))) same = false;
          if (same && m.get("_id") instanceof String id) matches.add(id);
        }
      if (matches.size() == 1) return matches.get(0);
    }
    throw new IllegalStateException(
        "Nessie creation acknowledged but ID is ambiguous; reconcile before retrying");
  }

  static String createdId(Map<String, Object> response) {
    if (response.get("objectCreated") instanceof Map<?, ?> m && m.get("_id") instanceof String id)
      return id;
    if (response.get("_id") instanceof String id) return id;
    return null;
  }

  String purchase(String account, String merchant, long cents, String date, String description)
      throws Exception {
    return create(
        "/accounts/" + account + "/purchases",
        Map.of(
            "merchant_id",
            merchant,
            "medium",
            "balance",
            "purchase_date",
            date,
            "amount",
            cents / 100.0,
            "description",
            description));
  }
}
