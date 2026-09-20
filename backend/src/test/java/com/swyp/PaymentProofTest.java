package com.swyp;

import static org.junit.jupiter.api.Assertions.*;

import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentProofTest {
  Map<String, Object> receipt() {
    return new HashMap<>(
        Map.of(
            "credentialId",
            "card-1",
            "amountCents",
            2599L,
            "currency",
            "USD",
            "merchant",
            "Kroger",
            "terminalId",
            "swyp-terminal-01",
            "nonce",
            "abcdefghijklmnopqrstuv",
            "timestamp",
            1789824000L));
  }

  @Test
  void signedAmountCannotBeChanged() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
    var keys = generator.generateKeyPair();
    var p = receipt();
    var signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(keys.getPrivate());
    signer.update(PaymentProof.canonical(p));
    p.put("signature", Base64.getEncoder().encodeToString(signer.sign()));
    String key = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    PaymentProof.verify(p, key);
    p.put("amountCents", 2600L);
    assertThrows(IllegalArgumentException.class, () -> PaymentProof.verify(p, key));
  }

  @Test
  void rejectsInjectionAndInvalidMoney() {
    var p = receipt();
    p.put("credentialId", "card\ninjected");
    assertThrows(IllegalArgumentException.class, () -> PaymentProof.canonical(p));
    var invalid = receipt();
    invalid.put("amountCents", 0L);
    assertThrows(IllegalArgumentException.class, () -> PaymentProof.canonical(invalid));
  }
}
