package com.swyp;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NessieTest {
  @Test
  void textAcknowledgementIsResolvedWithoutRepeatingPost() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var posts = new AtomicInteger();
    server.createContext(
        "/customers",
        e -> {
          String response;
          if (e.getRequestMethod().equals("POST")) {
            posts.incrementAndGet();
            response = "Customer created";
          } else
            response =
                "[{\"_id\":\"test-id\",\"first_name\":\"Swyp\",\"last_name\":\"Demo-unique\"}]";
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, bytes.length);
          e.getResponseBody().write(bytes);
          e.close();
        });
    server.start();
    try {
      var api = new Nessie("http://127.0.0.1:" + server.getAddress().getPort(), "test");
      assertEquals(
          "test-id",
          api.create("/customers", Map.of("first_name", "Swyp", "last_name", "Demo-unique")));
      assertEquals(1, posts.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void uncertainPostIsNeverAutomaticallyRetried() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var posts = new AtomicInteger();
    server.createContext(
        "/customers",
        e -> {
          posts.incrementAndGet();
          e.sendResponseHeaders(503, -1);
          e.close();
        });
    server.start();
    try {
      var api = new Nessie("http://127.0.0.1:" + server.getAddress().getPort(), "test");
      assertThrows(IllegalStateException.class, () -> api.create("/customers", Map.of()));
      assertEquals(1, posts.get());
    } finally {
      server.stop(0);
    }
  }
}
