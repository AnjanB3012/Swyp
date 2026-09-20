package com.swyp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.cloud.FirestoreClient;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class Server {
  public static void main(String[] args) throws Exception {
    FirebaseApp.initializeApp(
        FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .setProjectId(System.getenv("GOOGLE_CLOUD_PROJECT"))
            .build());
    var db = FirestoreClient.getFirestore();
    var bank = new Bank(db);
    var gemini = new Gemini();
    var deals = new Deals(db, gemini);
    bank.seedNearbyStores();
    if (Arrays.asList(args).contains("--seed-stores")) {
      db.close();
      bank.jobs.shutdown();
      return;
    }
    if (Arrays.asList(args).contains("--deals-once")) {
      deals.refresh();
      db.close();
      bank.jobs.shutdown();
      return;
    }
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    if (Boolean.parseBoolean(System.getenv().getOrDefault("DEALS_ENABLED", "false"))) {
      long delay =
          Duration.between(
                  Instant.now(),
                  LocalDate.now(ZoneOffset.UTC)
                      .plusDays(1)
                      .atStartOfDay()
                      .toInstant(ZoneOffset.UTC))
              .getSeconds();
      scheduler.scheduleAtFixedRate(
          () -> {
            try {
              deals.refresh();
            } catch (Exception e) {
              System.err.println("Deals refresh failed: " + e.getClass().getSimpleName());
            }
          },
          delay,
          86400,
          TimeUnit.SECONDS);
    }
    var server =
        HttpServer.create(
            new InetSocketAddress(Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"))),
            0);
    server.setExecutor(Executors.newFixedThreadPool(12));
    server.createContext(
        "/",
        exchange -> {
          try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/health") && exchange.getRequestMethod().equals("GET")) {
              send(exchange, 200, Map.of("status", "ok"));
              return;
            }
            if (!exchange.getRequestMethod().equals("POST")) {
              send(exchange, 405, Map.of("error", "POST required"));
              return;
            }
            byte[] bytes = exchange.getRequestBody().readNBytes(5_100_001);
            if (bytes.length > 5_100_000) {
              send(exchange, 413, Map.of("error", "Request too large"));
              return;
            }
            Map<String, Object> body =
                bytes.length == 0 ? Map.of() : Json.map(new String(bytes, StandardCharsets.UTF_8));
            if (path.equals("/v1/terminal/payment")) {
              send(exchange, 200, bank.processTerminal(body));
              return;
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
              send(exchange, 401, Map.of("error", "Sign in required"));
              return;
            }
            String uid;
            try {
              uid = FirebaseAuth.getInstance().verifyIdToken(auth.substring(7), true).getUid();
            } catch (Exception e) {
              send(exchange, 401, Map.of("error", "Session expired; sign in again"));
              return;
            }
            Object result =
                switch (path) {
                  case "/v1/initialize" -> bank.initialize(uid);
                  case "/v1/cards" -> {
                    bank.createCards(uid);
                    yield Map.of("status", "generating");
                  }
                  case "/v1/settings" -> {
                    bank.settings(uid, body);
                    yield Map.of("saved", true);
                  }
                  case "/v1/credentials" -> {
                    bank.registerKey(uid, body);
                    yield Map.of("registered", true);
                  }
                  case "/v1/payments/process" -> bank.process(uid, Json.str(body, "nonce"));
                  case "/v1/checkout" -> gemini.checkout(Json.str(body, "image"));
                  case "/v1/deals/refresh" -> deals.dailyRefreshIfNeeded(uid);
                  default -> null;
                };
            if (result == null) send(exchange, 404, Map.of("error", "Unknown endpoint"));
            else send(exchange, 200, result);
          } catch (IllegalArgumentException e) {
            send(
                exchange,
                400,
                Map.of("error", e.getMessage() == null ? "Invalid request" : e.getMessage()));
          } catch (Exception e) {
            System.err.println(
                "API failure: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            send(
                exchange,
                503,
                Map.of(
                    "error",
                    "Operation unavailable. Check account status and backend configuration before"
                        + " retrying."));
          } finally {
            exchange.close();
          }
        });
    server.start();
    System.out.println("Swyp backend listening on " + server.getAddress());
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  server.stop(1);
                  scheduler.shutdown();
                  bank.jobs.shutdown();
                  try {
                    db.close();
                  } catch (Exception ignored) {
                  }
                }));
  }

  static void send(HttpExchange e, int code, Object value) throws java.io.IOException {
    try {
      byte[] bytes = Json.write(value).getBytes(StandardCharsets.UTF_8);
      e.getResponseHeaders().set("Content-Type", "application/json");
      e.getResponseHeaders().set("Cache-Control", "no-store");
      e.sendResponseHeaders(code, bytes.length);
      e.getResponseBody().write(bytes);
    } catch (Exception ex) {
      throw new java.io.IOException(ex);
    }
  }
}
