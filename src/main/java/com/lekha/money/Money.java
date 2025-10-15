package com.lekha.money;

import java.math.BigInteger;

public record Money(Currency currency, BigInteger amountInMinorUnits) {

  public static Money zero(Currency currency) {
    return new Money(currency, BigInteger.ZERO);
  }

  public boolean isLessThan(Money other) {
    ensureCurrencyMatches(other);
    return amountInMinorUnits.compareTo(other.amountInMinorUnits) < 0;
  }

  public boolean isGreaterThan(Money other) {
    ensureCurrencyMatches(other);
    return amountInMinorUnits.compareTo(other.amountInMinorUnits) > 0;
  }

  public Money add(Money other) {
    ensureCurrencyMatches(other);
    return new Money(this.currency, this.amountInMinorUnits.add(other.amountInMinorUnits));
  }

  public Money subtract(Money other) {
    ensureCurrencyMatches(other);
    return new Money(this.currency, this.amountInMinorUnits.subtract(other.amountInMinorUnits));
  }

  private void ensureCurrencyMatches(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Currency " + currency + " does not match account " + other.currency());
    }
  }

  public void ensurePositive() {
    if (amountInMinorUnits.compareTo(BigInteger.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }
  }
}
