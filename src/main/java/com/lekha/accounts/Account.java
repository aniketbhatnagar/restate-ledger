package com.lekha.accounts;

import com.lekha.ledger.Ledger;
import com.lekha.money.Currency;
import com.lekha.money.Money;
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

  public record OperationMetadata(Optional<String> transactionId) {}

  public record DebitInstruction(Money amountToDebit, OperationMetadata metadata) {}

  public record DebitResult(AccountSummary accountSummary) {}

  public record CreditInstruction(Money amountToCredit, OperationMetadata metadata) {}

  public record CreditResult(AccountSummary accountSummary) {}

  public record HoldInstruction(String holdId, Money amountToHold, OperationMetadata metadata) {}

  public record ReleaseHoldInstruction(String holdId, OperationMetadata metadata) {}

  public record DebitFromHoldInstruction(String holdId, DebitInstruction debitInstruction) {}

  public record DebitFromHoldResult(AccountSummary accountSummary, HoldSummary holdSummary) {}

  public record AccountBalances(Money availableBalance, Money holdBalance) {}

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

      if (accountOptions.accountType().doDebitsDecreaseBalance()) {
        accountBalancesState.subtractAvailableBalance(amountToDebit);
      } else {
        accountBalancesState.addAvailableBalance(amountToDebit);
      }

      AccountSummary accountSummary =
          new AccountSummary(accountId, accountBalancesState.balances());

      LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, accountId);
      ledgerRecorder.recordBalanceChangeInLedger(
          "account_debit",
          amountToDebit,
          Ledger.Operation.DEBIT,
          accountSummary,
          instruction.metadata());

      return new DebitResult(accountSummary);
    }
  }

  @Handler
  public CreditResult credit(ObjectContext ctx, CreditInstruction instruction) {
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

      LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, accountId);
      ledgerRecorder.recordBalanceChangeInLedger(
          "account_credit",
          amountToCredit,
          Ledger.Operation.CREDIT,
          accountSummary,
          instruction.metadata());

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

      String holdId = instruction.holdId();
      try (HoldBalanceState holdBalanceState =
          HoldBalanceState.create(ctx, holdId, amountToHold.currency())) {
        holdBalanceState.addAvailableBalance(amountToHold);

        AccountSummary accountSummary =
            new AccountSummary(accountId, accountBalancesState.balances());
        HoldSummary holdSummary = new HoldSummary(holdId, holdBalanceState.availableBalance());

        LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, accountId);
        ledgerRecorder.recordBalanceChangeInLedger(
            "account_debit",
            amountToHold,
            Ledger.Operation.DEBIT,
            accountSummary,
            instruction.metadata());
        ledgerRecorder.recordHoldBalanceChangeInLedger(
            "hold_credit",
            amountToHold,
            Ledger.Operation.CREDIT,
            accountSummary,
            holdSummary,
            instruction.metadata());

        return new HoldResult(accountSummary, holdSummary);
      }
    }
  }

  @Handler
  public ReleaseHoldResult releaseHold(ObjectContext ctx, ReleaseHoldInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      String accountId = ctx.key();
      String holdId = instruction.holdId();

      if (!HoldBalanceState.exits(ctx, holdId)) {
        // nothing to release.
        AccountSummary accountSummary =
            new AccountSummary(accountId, accountBalancesState.balances());
        Currency currency = accountBalancesState.balances().holdBalance().currency();
        Money amountReleased = Money.zero(currency);
        HoldSummary holdSummary = new HoldSummary(holdId, amountReleased);
        return new ReleaseHoldResult(accountSummary, holdSummary, amountReleased);
      }

      try (HoldBalanceState holdBalanceState = HoldBalanceState.getExisting(ctx, holdId)) {
        Money amountToRelease = holdBalanceState.availableBalance();
        amountToRelease.ensurePositive();
        holdBalanceState.subtractAvailableBalance(amountToRelease);

        accountBalancesState.releaseHold(amountToRelease);

        AccountSummary accountSummary =
            new AccountSummary(accountId, accountBalancesState.balances());
        HoldSummary holdSummary =
            new HoldSummary(accountId, Money.zero(amountToRelease.currency()));

        LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, accountId);
        ledgerRecorder.recordBalanceChangeInLedger(
            "account_credit",
            amountToRelease,
            Ledger.Operation.CREDIT,
            accountSummary,
            instruction.metadata());
        ledgerRecorder.recordHoldBalanceChangeInLedger(
            "hold_debit",
            amountToRelease,
            Ledger.Operation.DEBIT,
            accountSummary,
            holdSummary,
            instruction.metadata());

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

  @Handler
  public DebitFromHoldResult debitFromHold(
      ObjectContext ctx, DebitFromHoldInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      String accountId = ctx.key();
      Money amountToDebit = instruction.debitInstruction().amountToDebit();
      String holdId = instruction.holdId();
      try (HoldBalanceState holdBalanceState = HoldBalanceState.getExisting(ctx, holdId)) {
        holdBalanceState.subtractAvailableBalance(amountToDebit);
        accountBalancesState.subtractHoldBalance(amountToDebit);

        AccountSummary accountSummary =
            new AccountSummary(accountId, accountBalancesState.balances());
        HoldSummary holdSummary = new HoldSummary(holdId, holdBalanceState.availableBalance());

        LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, accountId);
        ledgerRecorder.recordHoldBalanceChangeInLedger(
            "hold_debit",
            amountToDebit,
            Ledger.Operation.DEBIT,
            accountSummary,
            holdSummary,
            instruction.debitInstruction().metadata());
        return new DebitFromHoldResult(accountSummary, holdSummary);
      }
    }
  }
}
