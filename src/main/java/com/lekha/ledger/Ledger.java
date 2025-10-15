package com.lekha.ledger;

import com.lekha.accounts.Account;
import com.lekha.money.Money;
import com.lekha.transactions.TransactionMetadata;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;
import java.util.Optional;

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
      // If the balance change involves using a hold.
      Optional<Account.HoldSummary> holdSummary,
      TransactionMetadata transactionMetadata) {}

  public record RecordBalanceHoldInstruction(
      String idem,
      long timestampMs,
      Money amount,
      Account.AccountSummary accountSummary,
      Account.HoldSummary holdSummary,
      TransactionMetadata transactionMetadata) {}

  public record RecordBalanceHoldReleaseInstruction(
      String idem,
      long timestampMs,
      Money amount,
      Account.AccountSummary accountSummary,
      Account.HoldSummary holdSummary,
      TransactionMetadata transactionMetadata) {}

  @Handler
  public void recordBalanceChange(ObjectContext ctx, RecordBalanceChangeInstruction instruction) {
    // TODO: write to DB. In the DB, we can also store latest balance in addition to writing ledger
    // entries.
  }

  @Handler
  public void recordBalanceHold(ObjectContext ctx, RecordBalanceHoldInstruction instruction) {
    // TODO: write to DB.
  }

  @Handler
  public void recordBalanceHoldRelease(
      ObjectContext ctx, RecordBalanceHoldReleaseInstruction instruction) {
    // TODO: write to DB.
  }
}
