package com.lekha.account;

import com.lekha.clock.Clock;
import com.lekha.ledger.Ledger;
import com.lekha.ledger.LedgerClient;
import com.lekha.money.Money;
import dev.restate.sdk.ObjectContext;

public class LedgerRecorder {

  private final ObjectContext ctx;
  private final LedgerClient.ContextClient ledgerClient;

  public LedgerRecorder(ObjectContext ctx, String accountId) {
    this.ctx = ctx;
    this.ledgerClient = LedgerClient.fromContext(ctx, accountId);
  }

  public void recordBalanceChangeInLedger(
      String idemSuffix,
      Money amount,
      Ledger.Operation operation,
      Account.AccountSummary accountSummary,
      Account.OperationMetadata metadata) {
    Ledger.RecordBalanceChangeInstruction instruction =
        new Ledger.RecordBalanceChangeInstruction(
            ledgerIdem(idemSuffix),
            ledgerTimestampMs(),
            operation,
            amount,
            accountSummary,
            metadata);
    // Ledger entries can be posted async
    ledgerClient.recordBalanceChange(instruction);
  }

  public void recordHoldBalanceChangeInLedger(
      String idemSuffix,
      Money amount,
      Ledger.Operation operation,
      Account.AccountSummary accountSummary,
      Account.HoldSummary holdSummary,
      Account.OperationMetadata metadata) {
    Ledger.RecordHoldBalanceChangeInstruction instruction =
        new Ledger.RecordHoldBalanceChangeInstruction(
            ledgerIdem(idemSuffix),
            ledgerTimestampMs(),
            operation,
            amount,
            accountSummary,
            holdSummary,
            metadata);
    // Ledger entries can be posted async
    ledgerClient.recordHoldBalanceChange(instruction);
  }

  private String ledgerIdem(String idemSuffix) {
    return this.ctx.request().invocationId() + "_" + idemSuffix;
  }

  private long ledgerTimestampMs() {
    // Note: Clock.currentTimeMillis() will generate a new timestamp on a retry and that's ok
    // because state updates only apply on successful handler execution.
    return Clock.currentTimeMillis();
  }
}
