package com.lekha.accounts;

import com.lekha.money.Currency;
import com.lekha.money.Money;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.SharedObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class AccountBalancesState implements AutoCloseable {

  private static final StateKey<State> ACCOUNT_BALANCES_STATE_KEY =
      StateKey.of("account_balances_state", State.class);

  public record State(Money availableBalance, Money holdBalance) {
    public static State empty(Currency currency) {
      return new State(Money.zero(currency), Money.zero(currency));
    }
  }

  private final SharedObjectContext ctx;
  private State state;
  private boolean flushNeeded = false;

  private AccountBalancesState(SharedObjectContext ctx, State state, boolean flushNeeded) {
    this.ctx = ctx;
    this.state = state;
    this.flushNeeded = flushNeeded;
  }

  public static AccountBalancesState create(ObjectContext ctx, Currency currency) {
    if (ctx.get(ACCOUNT_BALANCES_STATE_KEY).isPresent()) {
      throw new TerminalException("account state already present");
    }
    State state = State.empty(currency);
    return new AccountBalancesState(ctx, state, true);
  }

  public static AccountBalancesState getExisting(SharedObjectContext ctx) {
    State state = getStateOrThrow(ctx);
    return new AccountBalancesState(ctx, state, false);
  }

  public void addAvailableBalance(Money amountToAdd) {
    this.updateAvailableBalance(availableBalance -> availableBalance.add(amountToAdd));
  }

  public void subtractAvailableBalance(Money amountToSubtract) {
    this.updateAvailableBalance(
        availableBalance -> {
          BalanceUpdateChecks.checkEnoughBalance(
              availableBalance, amountToSubtract, availableBalanceErrorContext());
          return availableBalance.subtract(amountToSubtract);
        });
  }

  public void hold(Money amountToHold) {
    this.updateState(
        state -> {
          Money availableBalance = state.availableBalance();
          Money holdBalance = state.holdBalance();
          BalanceUpdateChecks.checkEnoughBalance(
              availableBalance, amountToHold, availableBalanceErrorContext());
          return new State(availableBalance.subtract(amountToHold), holdBalance.add(amountToHold));
        });
  }

  public void releaseHold(Money amountToRelease) {
    this.updateState(
        state -> {
          Money availableBalance = state.availableBalance();
          Money holdBalance = state.holdBalance();
          BalanceUpdateChecks.checkEnoughBalance(
              holdBalance, amountToRelease, holdBalanceErrorContext());
          return new State(
              availableBalance.add(amountToRelease), holdBalance.subtract(amountToRelease));
        });
  }

  public void subtractHoldBalance(Money amountToSubtract) {
    this.updateHoldBalance(
        holdBalance -> {
          BalanceUpdateChecks.checkEnoughBalance(
              holdBalance, amountToSubtract, holdBalanceErrorContext());
          return holdBalance.subtract(amountToSubtract);
        });
  }

  public Account.AccountBalances balances() {
    return new Account.AccountBalances(this.state.availableBalance, this.state.holdBalance);
  }

  @Override
  public void close() {
    flushState();
  }

  public void flushState() {
    if (flushNeeded) {
      if (!(ctx instanceof ObjectContext)) {
        throw new TerminalException("Cannot flush state in shared context");
      }

      ((ObjectContext) ctx).set(ACCOUNT_BALANCES_STATE_KEY, state);
      flushNeeded = false;
    }
  }

  private void updateAvailableBalance(Function<Money, Money> mapper) {
    this.updateState(
        state -> {
          Money availableBalance = state.availableBalance();
          Money holdBalance = state.holdBalance();
          Money newAvailableBalance = mapper.apply(availableBalance);
          return new State(newAvailableBalance, holdBalance);
        });
  }

  private void updateHoldBalance(Function<Money, Money> mapper) {
    this.updateState(
        state -> {
          Money availableBalance = state.availableBalance();
          Money holdBalance = state.holdBalance();
          Money newHoldBalance = mapper.apply(holdBalance);
          return new State(availableBalance, newHoldBalance);
        });
  }

  private void updateState(Function<State, State> mapper) {
    this.state = mapper.apply(state);
    this.flushNeeded = true;
  }

  private String accountId() {
    return this.ctx.key();
  }

  private Supplier<Map<String, String>> availableBalanceErrorContext() {
    return () -> Map.of("account_id", accountId(), "balance_type", "available_balance");
  }

  private Supplier<Map<String, String>> holdBalanceErrorContext() {
    return () -> Map.of("account_id", accountId(), "balance_type", "hold_balance");
  }

  private static State getStateOrThrow(SharedObjectContext ctx) {
    return ctx.get(ACCOUNT_BALANCES_STATE_KEY)
        .orElseThrow(() -> new TerminalException("account state not present"));
  }
}
