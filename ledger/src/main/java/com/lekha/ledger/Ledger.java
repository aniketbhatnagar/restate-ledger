package com.lekha.ledger;

import com.lekha.account.Account;
import com.lekha.money.Money;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;

@VirtualObject
public class Ledger {

  public enum Operation {
    DEBIT,
    CREDIT
  }

  public record RecordBalanceChangeInstruction(
      String idem,
      long timestampMs,
      Operation operation,
      Money amount,
      Account.AccountSummary accountSummary,
      Account.OperationMetadata metadata) {}

  public record RecordHoldBalanceChangeInstruction(
      String idem,
      long timestampMs,
      Operation operation,
      Money amount,
      Account.AccountSummary accountSummary,
      Account.HoldSummary holdSummary,
      Account.OperationMetadata metadata) {}

  @Handler
  public void recordBalanceChange(ObjectContext ctx, RecordBalanceChangeInstruction instruction) {
    // TODO: write to DB. In the DB, we can also store latest balance in addition to writing ledger
    // entries.
  }

  @Handler
  public void recordHoldBalanceChange(
      ObjectContext ctx, RecordHoldBalanceChangeInstruction instruction) {
    // TODO: write to DB.
  }
}
