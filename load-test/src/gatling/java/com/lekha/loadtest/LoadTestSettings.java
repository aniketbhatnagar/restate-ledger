package com.lekha.loadtest;

import com.lekha.money.Currency;
import com.lekha.money.Money;
import java.math.BigInteger;
import java.time.Duration;

record LoadTestSettings(
    String baseUri,
    int liabilityAccounts,
    Money transferAmount,
    int concurrentUsers,
    Duration runDuration) {

  static LoadTestSettings fromEnvironment() {
    String baseUri =
        readString("ledger.load.baseUri", "LEDGER_LOAD_BASE_URI", "http://localhost:8080");
    int liabilityAccounts =
        readPositiveInt("ledger.load.liabilityAccounts", "LEDGER_LOAD_LIABILITY_ACCOUNTS", 10);
    int concurrentUsers =
        readPositiveInt("ledger.load.concurrentUsers", "LEDGER_LOAD_CONCURRENT_USERS", 10);
    int durationSeconds =
        readPositiveInt("ledger.load.durationSeconds", "LEDGER_LOAD_DURATION_SECONDS", 60);
    Money transferAmount =
        new Money(
            Currency.USD,
            readPositiveBigInteger(
                "ledger.load.transferMinorUnits", "LEDGER_LOAD_TRANSFER_MINOR_UNITS", "100"));

    if (liabilityAccounts < 1) {
      throw new IllegalArgumentException("liabilityAccounts must be at least 1");
    }

    return new LoadTestSettings(
        baseUri,
        liabilityAccounts,
        transferAmount,
        concurrentUsers,
        Duration.ofSeconds(durationSeconds));
  }

  private static String readString(String sysProp, String envVar, String defaultValue) {
    String sysValue = System.getProperty(sysProp);
    if (sysValue != null && !sysValue.isBlank()) {
      return sysValue;
    }
    String envValue = System.getenv(envVar);
    if (envValue != null && !envValue.isBlank()) {
      return envValue;
    }
    return defaultValue;
  }

  private static int readPositiveInt(String sysProp, String envVar, int defaultValue) {
    String value = readString(sysProp, envVar, Integer.toString(defaultValue));
    try {
      int parsed = Integer.parseInt(value);
      if (parsed <= 0) {
        throw new IllegalArgumentException(
            sysProp + "/" + envVar + " must be > 0 but was " + parsed);
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Unable to parse integer for " + sysProp + "/" + envVar + ": " + value, e);
    }
  }

  private static BigInteger readPositiveBigInteger(
      String sysProp, String envVar, String defaultValue) {
    String value = readString(sysProp, envVar, defaultValue);
    try {
      BigInteger parsed = new BigInteger(value);
      if (parsed.compareTo(BigInteger.ZERO) <= 0) {
        throw new IllegalArgumentException(
            sysProp + "/" + envVar + " must be > 0 but was " + value);
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Unable to parse integer for " + sysProp + "/" + envVar + ": " + value, e);
    }
  }
}
