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

public class HoldBalanceState implements AutoCloseable {

  public record State(Money availableBalance) {
    public static State empty(Currency currency) {
      return new State(Money.zero(currency));
    }
  }

  private static final String HOLD_STATE_KEY_PREFIX = "hold_";

  private final SharedObjectContext ctx;
  private final String holdId;
  private final StateKey<State> holdStateKey;
  private State state;
  private boolean flushNeeded = false;

  private HoldBalanceState(
      SharedObjectContext ctx, String holdId, State state, boolean flushNeeded) {
    this.ctx = ctx;
    this.holdId = holdId;
    this.holdStateKey = holdStateKey(holdId);
    this.state = state;
    this.flushNeeded = flushNeeded;
  }

  public static HoldBalanceState create(ObjectContext ctx, String holdId, Currency currency) {
    if (exits(ctx, holdId)) {
      throw new TerminalException("hold state already present");
    }
    return new HoldBalanceState(ctx, holdId, State.empty(currency), true);
  }

  public static boolean exits(ObjectContext ctx, String holdId) {
    return ctx.get(holdStateKey(holdId)).isPresent();
  }

  public static HoldBalanceState getExisting(SharedObjectContext ctx, String holdId) {
    State state = getStateOrThrow(ctx, holdId);
    return new HoldBalanceState(ctx, holdId, state, false);
  }

  @Override
  public void close() {
    flushState();
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

  private void updateAvailableBalance(Function<Money, Money> mapper) {
    updateState(
        state -> {
          Money availableBalance = state.availableBalance();
          Money newAvailableBalance = mapper.apply(availableBalance);
          return new State(newAvailableBalance);
        });
  }

  private void updateState(Function<State, State> mapper) {
    this.state = mapper.apply(state);
    this.flushNeeded = true;
  }

  public void flushState() {
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
