package com.swyp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Exercises the once-a-day, first-request-of-the-day deal search against the Firestore emulator
 * with a stubbed Gemini (no live web/LLM calls). Run with:
 * {@code firebase emulators:exec --only firestore 'gradle test --tests com.swyp.DealsDailyTest'}
 */
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class DealsDailyTest {
  private Firestore db() {
    return FirestoreOptions.getDefaultInstance().toBuilder()
        .setProjectId("demo-swyp-deals")
        .setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
        .setCredentials(
            GoogleCredentials.create(
                new AccessToken("owner", Date.from(Instant.now().plusSeconds(3600)))))
        .build()
        .getService();
  }

  /** A Gemini that never touches the network: it counts searches and returns fixed structuring. */
  static final class FakeGemini extends Gemini {
    final AtomicInteger searches = new AtomicInteger();
    volatile List<Map<String, Object>> structured;

    FakeGemini(List<Map<String, Object>> structured) {
      this.structured = structured;
    }

    @Override
    String groundedSearch(String prompt) {
      searches.incrementAndGet();
      return "Kroger 10% off produce this week; Walmart $5 off $25 groceries.";
    }

    @Override
    Object extract(String prompt, String image, Map<String, Object> schema) {
      return structured;
    }
  }

  @Test
  void firstRequestPublishesAndSubsequentRequestsAreCached() throws Exception {
    var db = db();
    try {
      // Clean slate.
      db.collection("dealsMeta").document("daily").delete().get();
      for (var d : db.collection("deals").get().get()) d.getReference().delete().get();
      db.collection("users").document("u1").set(Map.of("homeZip", "24060")).get();

      String future = LocalDate.now(ZoneOffset.UTC).plusDays(30).toString();
      var valid =
          new HashMap<String, Object>(
              Map.of(
                  "title", "10% off produce",
                  "merchant", "Kroger",
                  "product", "savor",
                  "bonusPercent", 10.0,
                  "minimumCents", 0,
                  "capCents", 0,
                  "expiresOn", future,
                  "url", "https://www.kroger.com/deal"));
      var expired =
          new HashMap<String, Object>(
              Map.of(
                  "title", "Old deal",
                  "merchant", "Walmart",
                  "product", "any",
                  "bonusPercent", 5.0,
                  "expiresOn", "2000-01-01",
                  "url", "https://www.walmart.com/x"));
      var gemini = new FakeGemini(List.of(valid, expired));
      var deals = new Deals(db, gemini);

      var first = deals.dailyRefreshIfNeeded("u1");
      assertEquals("done", first.get("status"));
      assertEquals("deals search done", first.get("note"));
      assertEquals(1, gemini.searches.get(), "search must run on the first request of the day");

      var published = db.collection("deals").whereEqualTo("verified", true).get().get();
      assertEquals(1, published.size(), "expired offer must be filtered out");
      var offer = published.getDocuments().get(0);
      assertEquals("Kroger", offer.getString("merchant"));
      assertEquals("daily-search", offer.getString("publishedVia"));
      assertEquals(Boolean.TRUE, offer.getBoolean("verified"));
      assertEquals("https://www.kroger.com/deal", offer.getString("sourceUrl"));

      // Same day, second request: cached, no re-search, no duplicate writes.
      var second = deals.dailyRefreshIfNeeded("u1");
      assertEquals("done", second.get("status"));
      assertEquals(1, gemini.searches.get(), "second request the same day must not re-run search");
      assertEquals(1, db.collection("deals").get().get().size());
    } finally {
      db.close();
    }
  }

  @Test
  void staleMetaFromYesterdayTriggersAFreshSearch() throws Exception {
    var db = db();
    try {
      db.collection("dealsMeta")
          .document("daily")
          .set(
              Map.of(
                  "date", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString(),
                  "status", "done",
                  "note", "deals search done"))
          .get();
      for (var d : db.collection("deals").get().get()) d.getReference().delete().get();
      db.collection("users").document("u2").set(Map.of("homeZip", "24060")).get();

      String future = LocalDate.now(ZoneOffset.UTC).plusDays(10).toString();
      var offer =
          new HashMap<String, Object>(
              Map.of(
                  "title", "Fuel points",
                  "merchant", "Kroger",
                  "product", "any",
                  "bonusPercent", 4.0,
                  "expiresOn", future,
                  "url", "https://www.kroger.com/fuel"));
      var gemini = new FakeGemini(List.of(offer));
      var deals = new Deals(db, gemini);

      var result = deals.dailyRefreshIfNeeded("u2");
      assertEquals("done", result.get("status"));
      assertEquals(1, gemini.searches.get(), "yesterday's marker must not block today's search");
      assertEquals(1, db.collection("deals").whereEqualTo("verified", true).get().get().size());
    } finally {
      db.close();
    }
  }
}
