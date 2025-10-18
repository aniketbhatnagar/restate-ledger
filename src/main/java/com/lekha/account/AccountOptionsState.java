package com.lekha.account;

import com.lekha.money.Currency;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.SharedObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;

public class AccountOptionsState {
  private static final StateKey<State> ACCOUNT_OPTIONS_KEY =
      StateKey.of("account_options", State.class);

  public record State(AccountType accountType, Currency nativeCurrency) {}

  private State state;

  private AccountOptionsState(State state) {
    this.state = state;
  }

  public static boolean exists(SharedObjectContext ctx) {
    return ctx.get(ACCOUNT_OPTIONS_KEY).isPresent();
  }

  public static AccountOptionsState create(
      ObjectContext ctx, Account.AccountOptions accountOptions) {
    if (exists(ctx)) {
      throw new TerminalException("Account options already exists");
    }
    State state = new State(accountOptions.accountType(), accountOptions.nativeCurrency());
    ctx.set(ACCOUNT_OPTIONS_KEY, state);
    return new AccountOptionsState(state);
  }

  public static AccountOptionsState getExisting(SharedObjectContext ctx) {
    State state = getStateOrThrow(ctx);
    return new AccountOptionsState(state);
  }

  public Account.AccountOptions accountOptions() {
    return new Account.AccountOptions(this.state.accountType(), this.state.nativeCurrency());
  }

  private static State getStateOrThrow(SharedObjectContext ctx) {
    return ctx.get(ACCOUNT_OPTIONS_KEY)
        .orElseThrow(() -> new TerminalException("account options not present"));
  }
}
