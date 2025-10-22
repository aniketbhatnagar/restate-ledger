package com.lekha.clock;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class Clock {

  public static long currentTimeMillis() {
    return LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
  }
}
