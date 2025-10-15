package com.lekha.money;

public enum Currency {
  USD(2),
  EUR(2),
  CHF(2),
  GBP(2);

  private final int numMinorUnits;

  Currency(int numMinorUnits) {
    this.numMinorUnits = numMinorUnits;
  }

  public int getNumMinorUnits() {
    return numMinorUnits;
  }
}
