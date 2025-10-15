package com.lekha.testsetup;

import static org.assertj.core.api.Assertions.assertThat;

import com.lekha.accounts.Account;
import com.lekha.accounts.AccountClient;
import com.lekha.accounts.AccountType;
import com.lekha.money.Currency;
import com.lekha.money.Money;
import dev.restate.client.Client;
import java.math.BigInteger;

public class AccountHelper {

  private final Client ingressClient;
  private final String accountId;
  private final AccountType accountType;
  private final Currency currency;
  private final AccountClient.IngressClient accountClient;

  public AccountHelper(
      Client ingressClient, String accountId, AccountType accountType, Currency currency) {
    this.ingressClient = ingressClient;
    this.accountId = accountId;
    this.accountType = accountType;
    this.currency = currency;
    this.accountClient = AccountClient.fromClient(this.ingressClient, accountId);

    Account.InitInstruction instruction =
        new Account.InitInstruction(new Account.AccountOptions(accountType, currency));
    accountClient.init(instruction);
  }

  public static AccountHelper newUSDAssetAccountHelper(Client ingressClient, String accountId) {
    return new AccountHelper(ingressClient, accountId, AccountType.ASSET, Currency.USD);
  }

  public static AccountHelper newUSDLiabilityAccountHelper(Client ingressClient, String accountId) {
    return new AccountHelper(ingressClient, accountId, AccountType.LIABILITY, Currency.USD);
  }

  public void assertAvailableBalance(int balance) {
    Account.AccountSummary accountSummary = accountClient.getSummary();
    assertThat(accountSummary.balances().availableBalance().currency()).isEqualTo(this.currency);
    assertThat(accountSummary.balances().availableBalance().amountInMinorUnits())
        .isEqualTo(balance);
  }

  public Account.HoldResult holdBalance(int holdAmount) {
    return accountClient.hold(
        new Account.HoldInstruction(
            new Money(this.currency, BigInteger.valueOf(holdAmount)),
            new Account.HoldOptions(TransactionMetadataFactory.transactionMetadata())));
  }

  public void assertHoldBalance(int balance) {
    Account.AccountSummary accountSummary = accountClient.getSummary();
    assertThat(accountSummary.balances().holdBalance().currency()).isEqualTo(this.currency);
    assertThat(accountSummary.balances().holdBalance().amountInMinorUnits()).isEqualTo(balance);
  }

  public AccountClient.IngressClient getAccountClient() {
    return accountClient;
  }
}
