package com.lekha.accounts;

import com.lekha.clock.Clock;
import com.lekha.ledger.Ledger;
import com.lekha.ledger.LedgerClient;
import com.lekha.money.Currency;
import com.lekha.money.Money;
import com.lekha.transactions.TransactionMetadata;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.SharedObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.TerminalException;
import java.util.Optional;

@VirtualObject
public class Account {

  public record AccountOptions(AccountType accountType, Currency nativeCurrency) {}

  public record InitInstruction(AccountOptions accountOptions) {}

  public record DebitOptions(Optional<String> holdId, TransactionMetadata transactionMetadata) {}

  public record DebitInstruction(Money amountToDebit, DebitOptions options) {}

  public record DebitResult(AccountSummary accountSummary, Optional<HoldSummary> holdSummary) {}

  public record CreditOptions(Optional<String> holdId, TransactionMetadata transactionMetadata) {}

  public record CreditInstruction(Money amountToCredit, CreditOptions options) {}

  public record CreditResult(AccountSummary accountSummary) {}

  public record HoldOptions(TransactionMetadata transactionMetadata) {}

  public record HoldInstruction(Money amountToHold, HoldOptions options) {}

  public record ReleaseHoldInstruction(String holdId, HoldOptions options) {}

  public record AccountBalances(Money availableBalance, Money holdBalance) {
    public static AccountBalances empty(Currency currency) {
      Money zeroBalance = Money.zero(currency);
      return new AccountBalances(zeroBalance, zeroBalance);
    }
  }

  public record AccountSummary(String accountId, AccountBalances balances) {}

  public record HoldSummary(String holdId, Money balance) {}

  public record HoldResult(AccountSummary accountSummary, HoldSummary holdSummary) {}

  public record ReleaseHoldResult(
      AccountSummary accountSummary, HoldSummary holdSummary, Money releasedAmount) {}

  @Handler
  public AccountSummary init(ObjectContext ctx, InitInstruction instruction) {
    if (!AccountOptionsState.exists(ctx)) {
      AccountOptionsState.create(ctx, instruction.accountOptions());
      try (AccountBalancesState accountBalancesState =
          AccountBalancesState.create(ctx, instruction.accountOptions().nativeCurrency())) {
        return new AccountSummary(ctx.key(), accountBalancesState.balances());
      }
    }

    return this.getSummary(ctx);
  }

  @Handler
  public DebitResult debit(ObjectContext ctx, DebitInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      Money amountToDebit = instruction.amountToDebit();
      String accountId = ctx.key();
      AccountOptions accountOptions = AccountOptionsState.getExisting(ctx).accountOptions();
      Optional<HoldSummary> holdSummary = Optional.empty();

      if (accountOptions.accountType().doDebitsDecreaseBalance()) {
        Optional<String> holdIdOpt = instruction.options().holdId();
        if (holdIdOpt.isPresent()) {
          String holdId = holdIdOpt.get();
          try (HoldBalanceState holdBalanceState = HoldBalanceState.getExisting(ctx, holdId)) {
            holdBalanceState.subtractAvailableBalance(amountToDebit);
            holdSummary = Optional.of(new HoldSummary(holdId, holdBalanceState.availableBalance()));

            accountBalancesState.subtractHoldBalance(amountToDebit);
          }
        } else {
          accountBalancesState.subtractAvailableBalance(amountToDebit);
        }
      } else {
        accountBalancesState.addAvailableBalance(amountToDebit);
      }

      AccountSummary accountSummary =
          new AccountSummary(accountId, accountBalancesState.balances());
      recordBalanceChangeInLedger(
          ctx,
          Ledger.Operation.DEBIT,
          amountToDebit,
          accountSummary,
          holdSummary,
          instruction.options().transactionMetadata);

      return new DebitResult(accountSummary, holdSummary);
    }
  }

  @Handler
  public CreditResult credit(ObjectContext ctx, CreditInstruction instruction) {
    // TODO: Support crediting to hold (needed in case of reversal)
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      Money amountToCredit = instruction.amountToCredit();
      String accountId = ctx.key();

      AccountOptions accountOptions = AccountOptionsState.getExisting(ctx).accountOptions();
      if (accountOptions.accountType().doCreditsDecreaseBalance()) {
        accountBalancesState.subtractAvailableBalance(amountToCredit);
      } else {
        accountBalancesState.addAvailableBalance(amountToCredit);
      }

      AccountSummary accountSummary =
          new AccountSummary(accountId, accountBalancesState.balances());
      recordBalanceChangeInLedger(
          ctx,
          Ledger.Operation.CREDIT,
          amountToCredit,
          accountSummary,
          Optional.empty(), // TODO: Credit to hold not supported.
          instruction.options().transactionMetadata());

      return new CreditResult(accountSummary);
    }
  }

  @Shared
  @Handler
  public AccountSummary getSummary(SharedObjectContext ctx) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      return new AccountSummary(ctx.key(), accountBalancesState.balances());
    }
  }

  @Handler
  public HoldResult hold(ObjectContext ctx, HoldInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      Money amountToHold = instruction.amountToHold();
      String accountId = ctx.key();

      AccountOptions accountOptions = AccountOptionsState.getExisting(ctx).accountOptions();
      if (!accountOptions.accountType().doDebitsDecreaseBalance()) {
        throw new TerminalException(
            "Cannot hold balances on this account. Account id: " + accountId);
      }

      accountBalancesState.hold(amountToHold);

      String holdId = ctx.random().nextUUID().toString();
      try (HoldBalanceState holdBalanceState =
          HoldBalanceState.create(ctx, holdId, amountToHold.currency())) {
        holdBalanceState.addAvailableBalance(amountToHold);

        AccountSummary accountSummary =
            new AccountSummary(accountId, accountBalancesState.balances());
        HoldSummary holdSummary = new HoldSummary(holdId, holdBalanceState.availableBalance());

        recordBalanceHoldInLedger(
            ctx,
            amountToHold,
            accountSummary,
            holdSummary,
            instruction.options().transactionMetadata());

        return new HoldResult(accountSummary, holdSummary);
      }
    }
  }

  @Handler
  public ReleaseHoldResult releaseHold(ObjectContext ctx, ReleaseHoldInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      String accountId = ctx.key();
      try (HoldBalanceState holdBalanceState =
          HoldBalanceState.getExisting(ctx, instruction.holdId)) {
        Money amountToRelease = holdBalanceState.availableBalance();
        amountToRelease.ensurePositive();
        holdBalanceState.subtractAvailableBalance(amountToRelease);

        accountBalancesState.releaseHold(amountToRelease);

        AccountSummary accountSummary =
            new AccountSummary(accountId, accountBalancesState.balances());
        HoldSummary holdSummary =
            new HoldSummary(accountId, Money.zero(amountToRelease.currency()));

        recordBalanceReleaseHoldInLedger(
            ctx,
            amountToRelease,
            accountSummary,
            holdSummary,
            instruction.options().transactionMetadata());

        return new ReleaseHoldResult(accountSummary, holdSummary, amountToRelease);
      }
    }
  }

  @Shared
  @Handler
  public HoldSummary getHoldSummary(SharedObjectContext ctx, String holdId) {
    try (HoldBalanceState holdBalanceState = HoldBalanceState.getExisting(ctx, holdId)) {
      return new HoldSummary(holdId, holdBalanceState.availableBalance());
    }
  }

  private void recordBalanceChangeInLedger(
      ObjectContext ctx,
      Ledger.Operation operation,
      Money amount,
      AccountSummary accountSummary,
      Optional<HoldSummary> holdSummary,
      TransactionMetadata transactionMetadata) {
    Ledger.RecordBalanceChangeInstruction instruction =
        new Ledger.RecordBalanceChangeInstruction(
            ledgerIdem(ctx),
            ledgerTimestampMs(),
            operation,
            amount,
            accountSummary,
            holdSummary,
            transactionMetadata);
    LedgerClient.ContextClient ledgerClient = LedgerClient.fromContext(ctx, ctx.key());
    // Ledger entries can be posted async
    ledgerClient.recordBalanceChange(instruction);
  }

  private void recordBalanceHoldInLedger(
      ObjectContext ctx,
      Money amount,
      AccountSummary accountSummary,
      Account.HoldSummary holdSummary,
      TransactionMetadata transactionMetadata) {
    Ledger.RecordBalanceHoldInstruction instruction =
        new Ledger.RecordBalanceHoldInstruction(
            ledgerIdem(ctx),
            ledgerTimestampMs(),
            amount,
            accountSummary,
            holdSummary,
            transactionMetadata);
    LedgerClient.ContextClient ledgerClient = LedgerClient.fromContext(ctx, ctx.key());
    // Ledger entries can be posted async
    ledgerClient.recordBalanceHold(instruction);
  }

  private void recordBalanceReleaseHoldInLedger(
      ObjectContext ctx,
      Money amount,
      AccountSummary accountSummary,
      Account.HoldSummary holdSummary,
      TransactionMetadata transactionMetadata) {
    Ledger.RecordBalanceHoldReleaseInstruction instruction =
        new Ledger.RecordBalanceHoldReleaseInstruction(
            ledgerIdem(ctx),
            ledgerTimestampMs(),
            amount,
            accountSummary,
            holdSummary,
            transactionMetadata);
    LedgerClient.ContextClient ledgerClient = LedgerClient.fromContext(ctx, ctx.key());
    // Ledger entries can be posted async
    ledgerClient.recordBalanceHoldRelease(instruction);
  }

  private static String ledgerIdem(ObjectContext ctx) {
    return ctx.request().invocationId().toString();
  }

  private static long ledgerTimestampMs() {
    // Note: Clock.currentTimeMillis() will generate a new timestamp on a retry and that's ok
    // because state updates only apply on successful handler execution.
    return Clock.currentTimeMillis();
  }
}
