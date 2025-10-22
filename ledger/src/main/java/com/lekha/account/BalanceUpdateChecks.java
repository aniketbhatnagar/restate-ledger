package com.lekha.account;

import com.lekha.money.Money;
import dev.restate.sdk.common.TerminalException;
import java.util.Map;
import java.util.function.Supplier;

public class BalanceUpdateChecks {

  public static void checkEnoughBalance(
      Money currentBalance, Money amountToSubtract, Supplier<Map<String, String>> errorContext) {
    if (currentBalance.isLessThan(amountToSubtract)) {
      throw new TerminalException(
          "Cannot take "
              + amountToSubtract.amountInMinorUnits()
              + " from current balance "
              + currentBalance.amountInMinorUnits()
              + ". Context: "
              + errorContext.get());
    }
  }
}
