package com.lekha.accounts;

import com.lekha.clock.Clock;
import com.lekha.ledger.Ledger;
import com.lekha.ledger.LedgerClient;
import com.lekha.money.Currency;
import com.lekha.money.Money;
import com.lekha.transactions.TransactionMetadata;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import java.util.Optional;

@VirtualObject
public class Account {

  public record AccountOptions(AccountType accountType, Currency nativeCurrency) {}

  public record InitInstruction(AccountOptions accountOptions) {}

  public record DebitOptions(Optional<String> holdId, TransactionMetadata transactionMetadata) {}

  public record DebitInstruction(Money amountToDebit, DebitOptions options) {}

  public record CreditOptions(Optional<String> holdId, TransactionMetadata transactionMetadata) {}

  public record CreditInstruction(Money amountToCredit, CreditOptions options) {}

  public record HoldOptions(TransactionMetadata transactionMetadata) {}

  public record HoldInstruction(Money amountToHold, HoldOptions options) {}

  public record AccountBalances(Money availableBalance, Money holdBalance) {
    public static AccountBalances empty(Currency currency) {
      Money zeroBalance = Money.zero(currency);
      return new AccountBalances(zeroBalance, zeroBalance);
    }

    public AccountBalances addAvailableBalance(Money amountToAdd) {
      return new AccountBalances(availableBalance.add(amountToAdd), holdBalance);
    }

    public AccountBalances subtractAvailableBalance(Money amountToSubtract) {
      return new AccountBalances(availableBalance.subtract(amountToSubtract), holdBalance);
    }

    public AccountBalances hold(Money amountToHold) {
      return new AccountBalances(
          availableBalance.subtract(amountToHold), holdBalance.add(amountToHold));
    }

    public AccountBalances subtractHoldBalance(Money amountToSubtract) {
      return new AccountBalances(availableBalance, holdBalance.subtract(amountToSubtract));
    }
  }

  public record AccountSummary(String accountId, AccountBalances balances) {}

  public record HoldSummary(String holdId, Money balance) {}

  public record HoldResult(AccountSummary accountSummary, HoldSummary holdSummary) {}

  public record AccountState(AccountBalances balances) {
    public static AccountState empty(Currency currency) {
      return new AccountState(AccountBalances.empty(currency));
    }
  }

  public record HoldState(Money balance) {}

  private static final StateKey<AccountState> ACCOUNT_STATE_KEY =
      StateKey.of("account_state", AccountState.class);
  private static final StateKey<AccountOptions> ACCOUNT_OPTIONS_KEY =
      StateKey.of("account_options", AccountOptions.class);
  private static final String HOLD_STATE_KEY_PREFIX = "hold_";

  @Handler
  public AccountSummary init(ObjectContext ctx, InitInstruction instruction) {
    if (ctx.get(ACCOUNT_OPTIONS_KEY).isEmpty()) {
      ctx.set(ACCOUNT_OPTIONS_KEY, instruction.accountOptions());
      AccountState state = AccountState.empty(instruction.accountOptions().nativeCurrency());
      ctx.set(ACCOUNT_STATE_KEY, state);
      return new AccountSummary(ctx.key(), state.balances());
    }
    AccountState accountState = getAccountStateOrThrow(ctx);
    return new AccountSummary(ctx.key(), accountState.balances());
  }

  @Handler
  public AccountSummary debit(ObjectContext ctx, DebitInstruction instruction) {
    AccountState accountState = getAccountStateOrThrow(ctx);
    AccountBalances currentBalances = accountState.balances();
    Money amountToDebit = instruction.amountToDebit();
    String accountId = ctx.key();

    AccountOptions accountOptions = getAccountOptionsOrThrow(ctx);
    AccountBalances newBalances;
    if (accountOptions.accountType().doDebitsDecreaseBalance()) {
      Optional<String> holdIdOpt = instruction.options().holdId();
      if (holdIdOpt.isPresent()) {
        String holdId = holdIdOpt.get();
        StateKey<HoldState> holdStateKey = holdStateKey(holdId);
        HoldState holdState = getHoldStateOrThrow(ctx, holdStateKey);
        Money holdBalance = holdState.balance();
        checkEnoughBalance(holdId, holdBalance, amountToDebit);
        Money newHoldBalance = holdBalance.subtract(amountToDebit);
        ctx.set(holdStateKey, new HoldState(newHoldBalance));

        newBalances = currentBalances.subtractHoldBalance(amountToDebit);
      } else {
        Money currentBalance = currentBalances.availableBalance();
        checkEnoughBalance(accountId, currentBalance, amountToDebit);
        newBalances = currentBalances.subtractAvailableBalance(amountToDebit);
      }
    } else {
      newBalances = currentBalances.addAvailableBalance(amountToDebit);
    }
    AccountState newState = new AccountState(newBalances);
    ctx.set(ACCOUNT_STATE_KEY, newState);

    AccountSummary accountSummary = new AccountSummary(accountId, newState.balances());
    recordBalanceChangeInLedger(
        ctx,
        Ledger.Operation.DEBIT,
        amountToDebit,
        accountSummary,
        instruction.options().holdId(),
        instruction.options().transactionMetadata);

    return accountSummary;
  }

  @Handler
  public AccountSummary credit(ObjectContext ctx, CreditInstruction instruction) {
    // TODO: Support crediting to hold (needed in case of reversal)
    AccountState accountState = getAccountStateOrThrow(ctx);
    AccountBalances currentBalances = accountState.balances();
    Money amountToCredit = instruction.amountToCredit();
    String accountId = ctx.key();

    AccountOptions accountOptions = getAccountOptionsOrThrow(ctx);
    Money currentBalance = currentBalances.availableBalance();
    AccountBalances newBalances;
    if (accountOptions.accountType().doCreditsDecreaseBalance()) {
      checkEnoughBalance(accountId, currentBalance, amountToCredit);
      newBalances = currentBalances.subtractAvailableBalance(amountToCredit);
    } else {
      newBalances = currentBalances.addAvailableBalance(amountToCredit);
    }
    AccountState newState = new AccountState(newBalances);
    ctx.set(ACCOUNT_STATE_KEY, newState);

    AccountSummary accountSummary = new AccountSummary(accountId, newState.balances());
    recordBalanceChangeInLedger(
        ctx,
        Ledger.Operation.CREDIT,
        amountToCredit,
        accountSummary,
        Optional.empty(), // TODO: Credit to hold not supported.
        instruction.options().transactionMetadata());

    return accountSummary;
  }

  @Handler
  public AccountSummary getSummary(ObjectContext ctx) {
    AccountState accountState = getAccountStateOrThrow(ctx);
    return new AccountSummary(ctx.key(), accountState.balances());
  }

  @Handler
  public HoldResult hold(ObjectContext ctx, HoldInstruction instruction) {
    AccountState accountState = getAccountStateOrThrow(ctx);
    AccountBalances currentBalances = accountState.balances();
    Money currentAvailableBalance = currentBalances.availableBalance();
    Money amountToHold = instruction.amountToHold();
    String accountId = ctx.key();

    AccountOptions accountOptions = getAccountOptionsOrThrow(ctx);
    if (!accountOptions.accountType().doDebitsDecreaseBalance()) {
      throw new TerminalException("Cannot hold balances on this account. Account id: " + accountId);
    }

    checkEnoughBalance(accountId, currentAvailableBalance, amountToHold);
    AccountBalances newBalances = currentBalances.hold(amountToHold);
    AccountState newState = new AccountState(newBalances);
    ctx.set(ACCOUNT_STATE_KEY, newState);

    String holdId = ctx.random().nextUUID().toString();
    HoldState holdState = new HoldState(amountToHold);
    ctx.set(holdStateKey(holdId), holdState);

    AccountSummary accountSummary = new AccountSummary(accountId, newState.balances());
    HoldSummary holdSummary = new HoldSummary(holdId, amountToHold);

    recordBalanceHoldInLedger(
        ctx,
        amountToHold,
        accountSummary,
        holdSummary,
        instruction.options().transactionMetadata());

    return new HoldResult(accountSummary, holdSummary);
  }

  @Handler
  public HoldSummary getHoldSummary(ObjectContext ctx, String holdId) {
    HoldState holdState = getHoldStateOrThrow(ctx, holdStateKey(holdId));
    return new HoldSummary(holdId, holdState.balance());
  }

  private void recordBalanceChangeInLedger(
      ObjectContext ctx,
      Ledger.Operation operation,
      Money amount,
      AccountSummary accountSummary,
      Optional<String> holdId,
      TransactionMetadata transactionMetadata) {
    Ledger.RecordBalanceChangeInstruction instruction =
        new Ledger.RecordBalanceChangeInstruction(
            ledgerIdem(ctx),
            ledgerTimestampMs(),
            operation,
            amount,
            accountSummary,
            holdId,
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

  private static void checkEnoughBalance(
      String accountOrHoldId, Money currentBalance, Money amountToSubtract) {
    if (currentBalance.isLessThan(amountToSubtract)) {
      throw new TerminalException(
          "Cannot take "
              + amountToSubtract.amountInMinorUnits()
              + " from current balance "
              + currentBalance.amountInMinorUnits()
              + " for account/hold id"
              + accountOrHoldId);
    }
  }

  private AccountState getAccountStateOrThrow(ObjectContext ctx) {
    return ctx.get(ACCOUNT_STATE_KEY)
        .orElseThrow(() -> new TerminalException("account state not present"));
  }

  private AccountOptions getAccountOptionsOrThrow(ObjectContext ctx) {
    return ctx.get(ACCOUNT_OPTIONS_KEY)
        .orElseThrow(() -> new TerminalException("account options not present"));
  }

  private HoldState getHoldStateOrThrow(ObjectContext ctx, StateKey<HoldState> holdStateStateKey) {
    return ctx.get(holdStateStateKey)
        .orElseThrow(() -> new TerminalException("hold state not present"));
  }

  private static StateKey<HoldState> holdStateKey(String holdId) {
    return StateKey.of(ACCOUNT_STATE_KEY + holdId, HoldState.class);
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
