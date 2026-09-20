package com.swyp;

import com.fasterxml.jackson.databind.*;
import java.util.*;

final class Json {
  static final ObjectMapper M = new ObjectMapper();

  static Map<String, Object> map(String value) throws Exception {
    return M.readValue(
        value, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
  }

  static String str(Map<String, Object> m, String k) {
    return Objects.toString(m.get(k), "");
  }

  static long num(Map<String, Object> m, String k) {
    Object v = m.get(k);
    if (!(v instanceof Number)) throw new IllegalArgumentException("Missing number: " + k);
    return ((Number) v).longValue();
  }

  static String write(Object value) throws Exception {
    return M.writeValueAsString(value);
  }
}
