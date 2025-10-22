package com.lekha.money;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.restate.sdk.common.TerminalException;
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
      throw new TerminalException(
          "Currency " + currency + " does not match account " + other.currency());
    }
  }

  public void ensurePositive() {
    if (amountInMinorUnits.compareTo(BigInteger.ZERO) <= 0) {
      throw new TerminalException("Amount must be positive");
    }
  }

  @JsonIgnore
  public boolean isZero() {
    return amountInMinorUnits.compareTo(BigInteger.ZERO) == 0;
  }

  public Money min(Money totalBalanceToDrain) {
    if (this.isLessThan(totalBalanceToDrain)) {
      return this;
    }
    return totalBalanceToDrain;
  }
}
