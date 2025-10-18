package com.lekha.account;

import com.lekha.ledger.Ledger;
import com.lekha.money.Currency;
import com.lekha.money.Money;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.SharedObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.TerminalException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@VirtualObject
public class Account {

  public record AccountOptions(AccountType accountType, Currency nativeCurrency) {}

  public record InitInstruction(AccountOptions accountOptions) {}

  public record OperationMetadata() {}

  public record DebitInstruction(Money amountToDebit, OperationMetadata metadata) {}

  public record AccountBalances(Money availableBalance, Money holdBalance) {}

  public record AccountSummary(String accountId, AccountBalances balances) {}

  public record DebitResult(AccountSummary accountSummary) {}

  public record CreditInstruction(Money amountToCredit, OperationMetadata metadata) {}

  public record CreditResult(AccountSummary accountSummary) {}

  public record HoldInstruction(String holdId, Money amountToHold, OperationMetadata metadata) {}

  public enum HoldType {
    // The hold was requested by user
    USER,
    // The hold is because of a transaction
    TRANSACTION
  }

  public record HoldSummary(String holdId, HoldType holdType, Money balance) {}

  public record HoldResult(AccountSummary accountSummary, HoldSummary holdSummary) {}

  public record ReleaseHoldInstruction(String holdId, OperationMetadata metadata) {}

  public record ReleaseHoldResult(
      AccountSummary accountSummary, HoldSummary holdSummary, Money releasedAmount) {}

  public record DebitHoldInstruction(String holdId, DebitInstruction debitInstruction) {}

  public record DebitHoldResult(AccountSummary accountSummary, HoldSummary holdSummary) {}

  public record CreditHoldInstruction(String holdId, CreditInstruction creditInstruction) {}

  public record CreditHoldResult(AccountSummary accountSummary, HoldSummary holdSummary) {}

  public record TransactionalDebitInstruction(
      String transactionId, DebitInstruction debitInstruction) {}

  public record TransactionalDebitResult(
      AccountSummary accountSummary, HoldSummary transactionHoldSummary) {}

  public record TransactionalCreditInstruction(
      String transactionId, CreditInstruction creditInstruction) {}

  public record TransactionalCreditResult(
      AccountSummary accountSummary, HoldSummary transactionHoldSummary) {}

  public record TransactionalReleaseHoldInstruction(
      String transactionId, OperationMetadata metadata) {}

  public record TransactionalReleaseHoldResult(
      AccountSummary accountSummary, HoldSummary transactionHoldSummary, Money releasedAmount) {}

  public record TransactionalHoldInstruction(
      String transactionId, Money amountToHold, OperationMetadata metadata) {}

  @Handler
  public AccountSummary init(ObjectContext ctx, InitInstruction instruction) {
    if (!AccountOptionsState.exists(ctx)) {
      AccountOptionsState.create(ctx, instruction.accountOptions());
      try (AccountBalancesState accountBalancesState =
          AccountBalancesState.create(ctx, instruction.accountOptions().nativeCurrency())) {
        return accountBalancesState.accountSummary();
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

      AccountSummary accountSummary = accountBalancesState.accountSummary();

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

      AccountSummary accountSummary = accountBalancesState.accountSummary();

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
      return accountBalancesState.accountSummary();
    }
  }

  @Handler
  public HoldResult hold(ObjectContext ctx, HoldInstruction instruction) {
    return hold(ctx, HoldType.USER, instruction);
  }

  private HoldResult hold(ObjectContext ctx, HoldType holdType, HoldInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      Money amountToHold = instruction.amountToHold();
      String accountId = ctx.key();

      AccountOptions accountOptions = AccountOptionsState.getExisting(ctx).accountOptions();
      if (!accountOptions.accountType().doDebitsDecreaseBalance()) {
        throw new TerminalException(
            "Cannot hold balances on this account. Account id: " + accountId);
      }

      String holdId = instruction.holdId();
      try (HoldBalanceState holdBalanceState =
          HoldBalanceState.create(ctx, holdId, holdType, amountToHold.currency())) {
        accountBalancesState.hold(amountToHold);
        holdBalanceState.addAvailableBalance(amountToHold);

        AccountSummary accountSummary = accountBalancesState.accountSummary();
        HoldSummary holdSummary = holdBalanceState.holdSummary();

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
    return releaseHold(ctx, HoldType.USER, instruction);
  }

  private ReleaseHoldResult releaseHold(
      ObjectContext ctx, HoldType holdType, ReleaseHoldInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      String accountId = ctx.key();
      String holdId = instruction.holdId();

      if (!HoldBalanceState.exits(ctx, holdId)) {
        // nothing to release.
        AccountSummary accountSummary = accountBalancesState.accountSummary();
        Currency currency = accountBalancesState.balances().holdBalance().currency();
        Money amountReleased = Money.zero(currency);
        HoldSummary holdSummary = new HoldSummary(holdId, holdType, amountReleased);
        return new ReleaseHoldResult(accountSummary, holdSummary, amountReleased);
      }

      try (HoldBalanceState holdBalanceState =
          HoldBalanceState.getExisting(ctx, holdId, holdType)) {
        Money amountToRelease = holdBalanceState.availableBalance();
        amountToRelease.ensurePositive();
        holdBalanceState.subtractAvailableBalance(amountToRelease);

        accountBalancesState.releaseHold(amountToRelease);

        AccountSummary accountSummary = accountBalancesState.accountSummary();
        HoldSummary holdSummary = holdBalanceState.holdSummary();

        LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, accountId);
        ledgerRecorder.recordHoldBalanceChangeInLedger(
            "hold_debit",
            amountToRelease,
            Ledger.Operation.DEBIT,
            accountSummary,
            holdSummary,
            instruction.metadata());
        ledgerRecorder.recordBalanceChangeInLedger(
            "account_credit",
            amountToRelease,
            Ledger.Operation.CREDIT,
            accountSummary,
            instruction.metadata());

        return new ReleaseHoldResult(accountSummary, holdSummary, amountToRelease);
      }
    }
  }

  @Shared
  @Handler
  public HoldSummary getHoldSummary(SharedObjectContext ctx, String holdId) {
    try (HoldBalanceState holdBalanceState =
        HoldBalanceState.getExisting(ctx, holdId, HoldType.USER)) {
      return holdBalanceState.holdSummary();
    }
  }

  @Shared
  @Handler
  public HoldSummary getTransactionalHoldSummary(SharedObjectContext ctx, String holdId) {
    try (HoldBalanceState holdBalanceState =
        HoldBalanceState.getExisting(ctx, holdId, HoldType.TRANSACTION)) {
      return holdBalanceState.holdSummary();
    }
  }

  @Handler
  public DebitHoldResult debitHold(ObjectContext ctx, DebitHoldInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      String accountId = ctx.key();
      Money amountToDebit = instruction.debitInstruction().amountToDebit();
      String holdId = instruction.holdId();
      try (HoldBalanceState holdBalanceState =
          HoldBalanceState.getExisting(ctx, holdId, HoldType.USER)) {
        holdBalanceState.subtractAvailableBalance(amountToDebit);
        accountBalancesState.subtractHoldBalance(amountToDebit);

        AccountSummary accountSummary = accountBalancesState.accountSummary();
        HoldSummary holdSummary = holdBalanceState.holdSummary();

        LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, accountId);
        ledgerRecorder.recordHoldBalanceChangeInLedger(
            "hold_debit",
            amountToDebit,
            Ledger.Operation.DEBIT,
            accountSummary,
            holdSummary,
            instruction.debitInstruction().metadata());
        return new DebitHoldResult(accountSummary, holdSummary);
      }
    }
  }

  @Handler
  public CreditHoldResult creditHold(ObjectContext ctx, CreditHoldInstruction instruction) {
    return creditHold(ctx, HoldType.USER, instruction.holdId(), instruction.creditInstruction());
  }

  private CreditHoldResult creditHold(
      ObjectContext ctx, HoldType holdType, String holdId, CreditInstruction instruction) {
    try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
      String accountId = ctx.key();
      Money amountToCredit = instruction.amountToCredit();
      try (HoldBalanceState transactionHoldState =
          HoldBalanceState.getExistingOrCreate(ctx, holdId, holdType, amountToCredit.currency())) {
        accountBalancesState.addHoldBalance(amountToCredit);
        transactionHoldState.addAvailableBalance(amountToCredit);

        AccountSummary accountSummary = accountBalancesState.accountSummary();
        HoldSummary holdSummary = transactionHoldState.holdSummary();
        LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, accountId);
        ledgerRecorder.recordHoldBalanceChangeInLedger(
            "hold_credit",
            amountToCredit,
            Ledger.Operation.CREDIT,
            accountSummary,
            holdSummary,
            instruction.metadata());

        return new CreditHoldResult(accountSummary, holdSummary);
      }
    }
  }

  @Handler
  public TransactionalDebitResult transactionalDebit(
      ObjectContext ctx, TransactionalDebitInstruction instruction) {
    AccountOptionsState accountOptionsState = AccountOptionsState.getExisting(ctx);
    if (accountOptionsState.accountOptions().accountType().doDebitsDecreaseBalance()) {
      try (AccountBalancesState accountBalancesState = AccountBalancesState.getExisting(ctx)) {
        Money amountToDebit = instruction.debitInstruction().amountToDebit();
        String transactionHoldId = instruction.transactionId();
        try (HoldBalanceState transactionHoldState =
                 HoldBalanceState.getExistingOrCreate(
                     ctx, transactionHoldId, HoldType.TRANSACTION, amountToDebit.currency())) {
          // First debit from transaction hold and then account's available balance.
          drain(
              ctx,
              amountToDebit,
              List.of(transactionHoldState),
              accountBalancesState,
              instruction.debitInstruction().metadata());

          return new TransactionalDebitResult(
              accountBalancesState.accountSummary(), transactionHoldState.holdSummary());
        }
      }
    } else {
      // no transaction support for asset accounts
      DebitResult debitResult = this.debit(ctx, instruction.debitInstruction());
      HoldSummary emptyHold =
          new HoldSummary(
              instruction.transactionId(),
              HoldType.TRANSACTION,
              Money.zero(instruction.debitInstruction().amountToDebit().currency()));
      return new TransactionalDebitResult(debitResult.accountSummary(), emptyHold);
    }
  }

  private void drain(
      ObjectContext ctx,
      Money totalBalanceToDrain,
      List<HoldBalanceState> holds,
      AccountBalancesState account,
      OperationMetadata metadata) {
    Money totalBalance =
        holds.stream()
            .map(HoldBalanceState::availableBalance)
            .reduce(account.availableBalance(), Money::add);
    BalanceUpdateChecks.checkEnoughBalance(
        totalBalance,
        totalBalanceToDrain,
        () ->
            Map.of(
                "holds",
                holds.stream().map(HoldBalanceState::holdId).collect(Collectors.joining(",")),
                "account",
                account.accountId()));

    // First drain from holds
    record DrainedHold(HoldBalanceState hold, Money drainedBalance) {}
    List<DrainedHold> drainedHolds = new ArrayList<>(holds.size());
    for (HoldBalanceState hold : holds) {
      if (totalBalanceToDrain.isZero()) {
        break;
      }
      totalBalanceToDrain.ensurePositive();

      Money holdBalanceToDrain = hold.availableBalance().min(totalBalanceToDrain);
      if (!holdBalanceToDrain.isZero()) {
        hold.subtractAvailableBalance(holdBalanceToDrain);
        account.subtractHoldBalance(holdBalanceToDrain);
        drainedHolds.add(new DrainedHold(hold, holdBalanceToDrain));
        totalBalanceToDrain = totalBalanceToDrain.subtract(holdBalanceToDrain);
      }
    }

    Money accountBalanceDrained = Money.zero(totalBalanceToDrain.currency());
    if (totalBalanceToDrain.isGreaterThan(Money.zero(totalBalanceToDrain.currency()))) {
      // finally drain account balance
      account.subtractAvailableBalance(totalBalanceToDrain);
      accountBalanceDrained = totalBalanceToDrain;
      totalBalanceToDrain = totalBalanceToDrain.subtract(accountBalanceDrained);
    }

    if (!totalBalanceToDrain.isZero()) {
      throw new IllegalStateException("totalBalanceToDrain is not 0. This should not happen.");
    }

    AccountSummary accountSummary = account.accountSummary();
    LedgerRecorder ledgerRecorder = new LedgerRecorder(ctx, account.accountId());
    for (DrainedHold drainedHold : drainedHolds) {
      ledgerRecorder.recordHoldBalanceChangeInLedger(
          "hold_debit_" + drainedHold.hold.holdId(),
          drainedHold.drainedBalance,
          Ledger.Operation.DEBIT,
          accountSummary,
          drainedHold.hold.holdSummary(),
          metadata);
    }
    if (!accountBalanceDrained.isZero()) {
      ledgerRecorder.recordBalanceChangeInLedger(
          "account_debit", accountBalanceDrained, Ledger.Operation.DEBIT, accountSummary, metadata);
    }
  }

  @Handler
  public TransactionalReleaseHoldResult transactionReleaseHold(
      ObjectContext ctx, TransactionalReleaseHoldInstruction instruction) {
    ReleaseHoldResult releaseHoldResult =
        releaseHold(
            ctx,
            HoldType.TRANSACTION,
            new ReleaseHoldInstruction(instruction.transactionId(), instruction.metadata()));
    return new TransactionalReleaseHoldResult(
        releaseHoldResult.accountSummary(),
        releaseHoldResult.holdSummary(),
        releaseHoldResult.releasedAmount());
  }

  @Handler
  public TransactionalCreditResult transactionalCredit(
      ObjectContext ctx, TransactionalCreditInstruction instruction) {
    AccountOptionsState accountOptionsState = AccountOptionsState.getExisting(ctx);
    if (accountOptionsState.accountOptions().accountType().doCreditsDecreaseBalance()) {
      // no transaction support for asset accounts
      CreditResult creditResult = this.credit(ctx, instruction.creditInstruction());
      HoldSummary emptyHold =
          new HoldSummary(
              instruction.transactionId(),
              HoldType.TRANSACTION,
              Money.zero(instruction.creditInstruction().amountToCredit().currency()));
      return new TransactionalCreditResult(creditResult.accountSummary(), emptyHold);
    } else {
      CreditHoldResult creditHoldResult =
          creditHold(
              ctx,
              HoldType.TRANSACTION,
              instruction.transactionId(),
              instruction.creditInstruction());
      return new TransactionalCreditResult(
          creditHoldResult.accountSummary(), creditHoldResult.holdSummary());
    }
  }

  @Handler
  public HoldResult transactionalHold(ObjectContext ctx, TransactionalHoldInstruction instruction) {
    return hold(
        ctx,
        HoldType.TRANSACTION,
        new HoldInstruction(
            instruction.transactionId(), instruction.amountToHold(), instruction.metadata()));
  }
}
