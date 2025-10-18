package com.lekha.account;

public enum AccountType {
  ASSET(false),
  LIABILITY(true);

  private final boolean debitsDecreaseBalance;

  AccountType(boolean debitsDecreaseBalance) {
    this.debitsDecreaseBalance = debitsDecreaseBalance;
  }

  public boolean doDebitsDecreaseBalance() {
    return debitsDecreaseBalance;
  }

  public boolean doCreditsDecreaseBalance() {
    return !debitsDecreaseBalance;
  }
}
