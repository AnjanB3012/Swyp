package com.swyp;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;

final class PaymentProof {
  static byte[] canonical(Map<String, Object> p) {
    long amount = Json.num(p, "amountCents"), time = Json.num(p, "timestamp");
    String card = Json.str(p, "credentialId"),
        merchant = Json.str(p, "merchant"),
        terminal = Json.str(p, "terminalId"),
        nonce = Json.str(p, "nonce");
    if (amount < 1
        || amount > 100_000_000
        || time <= 0
        || !Json.str(p, "currency").equals("USD")
        || merchant.isBlank()
        || merchant.length() > 80
        || merchant.chars().anyMatch(c -> c < 32 || c == 127)
        || !card.matches("[A-Za-z0-9_-]{1,64}")
        || !terminal.matches("[A-Za-z0-9._-]{1,64}")
        || !nonce.matches("[A-Za-z0-9_-]{22}"))
      throw new IllegalArgumentException("Invalid payment proof");
    return String.join(
            "\n",
            "swyp-v2",
            Long.toString(amount),
            "USD",
            merchant,
            terminal,
            nonce,
            Long.toString(time),
            card)
        .getBytes(StandardCharsets.UTF_8);
  }

  static void verify(Map<String, Object> p, String pinnedKey) throws Exception {
    var key =
        KeyFactory.getInstance("EC")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pinnedKey)));
    var signature = Signature.getInstance("SHA256withECDSA");
    signature.initVerify(key);
    signature.update(canonical(p));
    if (!signature.verify(Base64.getDecoder().decode(Json.str(p, "signature"))))
      throw new IllegalArgumentException("Invalid device signature");
  }
}
