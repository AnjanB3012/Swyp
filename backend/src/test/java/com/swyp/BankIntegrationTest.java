package com.swyp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class BankIntegrationTest {
  @Test
  void blankGenerationPostingReplayAndUncertainOutcome() throws Exception {
    var db =
        FirestoreOptions.getDefaultInstance().toBuilder()
            .setProjectId("demo-swyp-bank")
            .setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
            .setCredentials(
                GoogleCredentials.create(
                    new AccessToken("owner", Date.from(Instant.now().plusSeconds(3600)))))
            .build()
            .getService();
    var remote = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var purchases = new AtomicInteger();
    var fail = new java.util.concurrent.atomic.AtomicBoolean(false);
    remote.createContext(
        "/",
        e -> {
          if (e.getRequestURI().getPath().endsWith("/purchases")) purchases.incrementAndGet();
          if (fail.get()) {
            e.sendResponseHeaders(503, -1);
            e.close();
            return;
          }
          String id = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
          byte[] response =
              ("{\"objectCreated\":{\"_id\":\"" + id + "\"}}").getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(201, response.length);
          e.getResponseBody().write(response);
          e.close();
        });
    remote.start();
    var bank =
        new Bank(db, new Nessie("http://127.0.0.1:" + remote.getAddress().getPort(), "test"));
    String uid = "test-" + UUID.randomUUID();
    try {
      db.document("config/app").set(Map.of("auto-generate-transactions", false)).get();
      bank.initialize(uid);
      assertEquals("profile_required", bank.get(bank.user(uid)).get("status"));
      bank.settings(
          uid,
          Map.of(
              "fullName", "Alex Morgan",
              "age", 29L,
              "phone", "540-555-0100",
              "homeZip", "24060"));
      assertEquals("ready", bank.get(bank.user(uid)).get("status"));
      assertTrue(bank.user(uid).collection("cards").get().get().isEmpty());
      assertEquals(0, purchases.get());
      bank.provision(uid, true);
      var cards = bank.user(uid).collection("cards").get().get().getDocuments();
      assertEquals(3, cards.size());
      assertTrue(bank.user(uid).collection("transactions").get().get().size() >= 300);
      assertEquals(3, bank.user(uid).collection("bills").get().get().size());
      String card = cards.get(0).getId();
      long before = cards.get(0).getLong("balanceCents");
      var generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
      var keys = generator.generateKeyPair();
      bank.registerKey(
          uid,
          Map.of(
              "cardId",
              card,
              "publicKey",
              Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())));
      var proof = proof(card, "abcdefghijklmnopqrstuv", keys.getPrivate());
      int remoteBefore = purchases.get();
      assertEquals("posted", bank.processTerminal(proof).get("status"));
      assertEquals("posted", bank.processTerminal(proof).get("status"));
      assertEquals(remoteBefore + 1, purchases.get());
      assertEquals(
          before + 2599,
          bank.user(uid).collection("cards").document(card).get().get().getLong("balanceCents"));
      bank.registerKey(
          uid,
          Map.of(
              "cardId",
              card,
              "publicKey",
              Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())));
      var uncertainProof = proof(card, "bbcdefghijklmnopqrstuv", keys.getPrivate());
      fail.set(true);
      assertThrows(IllegalStateException.class, () -> bank.processTerminal(uncertainProof));
      assertEquals(
          "needs_reconciliation",
          bank.get(bank.user(uid).collection("paymentRequests").document("bbcdefghijklmnopqrstuv"))
              .get("status"));
      int count = purchases.get();
      assertThrows(Exception.class, () -> bank.processTerminal(uncertainProof));
      assertEquals(count, purchases.get());
    } finally {
      bank.jobs.shutdownNow();
      remote.stop(0);
      db.close();
    }
  }

  private Map<String, Object> proof(String card, String nonce, PrivateKey key) throws Exception {
    var p =
        new HashMap<String, Object>(
            Map.of(
                "credentialId",
                card,
                "amountCents",
                2599L,
                "currency",
                "USD",
                "merchant",
                "Kroger",
                "terminalId",
                "swyp-terminal-01",
                "nonce",
                nonce,
                "timestamp",
                Instant.now().getEpochSecond()));
    var signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(key);
    signer.update(PaymentProof.canonical(p));
    p.put("signature", Base64.getEncoder().encodeToString(signer.sign()));
    return p;
  }
}
