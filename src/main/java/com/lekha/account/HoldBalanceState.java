package com.lekha.account;

import com.lekha.money.Currency;
import com.lekha.money.Money;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.SharedObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class HoldBalanceState implements AutoCloseable {

  public record HoldDetails(String holdId, Account.HoldType holdType) {}

  public record State(HoldDetails holdDetails, Money availableBalance) {
    public static State empty(HoldDetails holdDetails, Currency currency) {
      return new State(holdDetails, Money.zero(currency));
    }
  }

  private static final String HOLD_STATE_KEY_PREFIX = "hold_";

  private final SharedObjectContext ctx;
  private final String holdId;
  private final StateKey<State> holdStateKey;
  private State state;
  private boolean flushNeeded = false;

  private HoldBalanceState(SharedObjectContext ctx, String holdId, State state) {
    this.ctx = ctx;
    this.holdId = holdId;
    this.holdStateKey = holdStateKey(holdId);
    this.state = state;
    this.flushNeeded = false;
  }

  public static HoldBalanceState create(
      ObjectContext ctx, String holdId, Account.HoldType holdType, Currency currency) {
    if (exits(ctx, holdId)) {
      throw new TerminalException("hold state already present");
    }
    return new HoldBalanceState(
        ctx, holdId, State.empty(new HoldDetails(holdId, holdType), currency));
  }

  public static boolean exits(ObjectContext ctx, String holdId) {
    return ctx.get(holdStateKey(holdId)).isPresent();
  }

  public static HoldBalanceState getExisting(SharedObjectContext ctx, String holdId) {
    State state = getStateOrThrow(ctx, holdId);
    return new HoldBalanceState(ctx, holdId, state);
  }

  public static HoldBalanceState getExistingOrCreate(
      ObjectContext ctx, String holdId, Account.HoldType holdType, Currency currency) {
    if (exits(ctx, holdId)) {
      return getExisting(ctx, holdId);
    }
    return create(ctx, holdId, holdType, currency);
  }

  @Override
  public void close() {
    flush();
  }

  public void addAvailableBalance(Money amountToAdd) {
    updateAvailableBalance(availableBalance -> availableBalance.add(amountToAdd));
  }

  public void subtractAvailableBalance(Money amountToSubtract) {
    updateAvailableBalance(
        availableBalance -> {
          BalanceUpdateChecks.checkEnoughBalance(
              availableBalance, amountToSubtract, availableBalanceErrorContext());
          return availableBalance.subtract(amountToSubtract);
        });
  }

  public Money availableBalance() {
    return this.state.availableBalance();
  }

  public String holdId() {
    return holdId;
  }

  private void updateAvailableBalance(Function<Money, Money> mapper) {
    updateState(
        state -> {
          Money availableBalance = state.availableBalance();
          Money newAvailableBalance = mapper.apply(availableBalance);
          return new State(state.holdDetails(), newAvailableBalance);
        });
  }

  private void updateState(Function<State, State> mapper) {
    this.state = mapper.apply(state);
    this.flushNeeded = true;
  }

  public void flush() {
    if (flushNeeded) {
      if (!(ctx instanceof ObjectContext)) {
        throw new TerminalException("Cannot flush state in shared context");
      }

      if (state.availableBalance.isZero()) {
        ((ObjectContext) ctx).clear(holdStateKey);
      } else {
        ((ObjectContext) ctx).set(holdStateKey, state);
      }
      flushNeeded = false;
    }
  }

  public Account.HoldSummary holdSummary() {
    return new Account.HoldSummary(
        holdId, state.holdDetails().holdType(), state.availableBalance());
  }

  public void ensureHoldType(Account.HoldType holdType) {
    if (!state.holdDetails().holdType().equals(holdType)) {
      throw new TerminalException(
          "Hold type mismatch: expected " + holdType + ", got " + state.holdDetails().holdType());
    }
  }

  private Supplier<Map<String, String>> availableBalanceErrorContext() {
    return () -> Map.of("hold_id", holdId);
  }

  private static StateKey<State> holdStateKey(String holdId) {
    return StateKey.of(HOLD_STATE_KEY_PREFIX + holdId, State.class);
  }

  private static State getStateOrThrow(SharedObjectContext ctx, String holdId) {
    return ctx.get(holdStateKey(holdId))
        .orElseThrow(() -> new TerminalException("account state not present"));
  }
}
