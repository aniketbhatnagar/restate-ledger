package com.lekha.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.lekha.money.Currency;
import com.lekha.money.Money;
import com.lekha.testsetup.BaseRestateTest;
import com.lekha.testsetup.TransactionMetadataFactory;
import dev.restate.client.Client;
import dev.restate.sdk.testing.RestateClient;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AccountTest extends BaseRestateTest {

  private static final Currency TEST_CURRENCY = Currency.USD;

  private AccountClient.IngressClient accountClient;

  @BeforeEach
  public void setup(@RestateClient Client ingressClient) {
    accountClient =
        AccountClient.fromClient(ingressClient, "test-account-" + UUID.randomUUID().toString());
  }

  @Test
  public void init_initializesState() {
    Account.AccountSummary accountSummary = initAccount(AccountType.LIABILITY);
    assertAccountSummaryBalances(accountSummary, 0, 0);
  }

  @Test
  public void liabilityAccountDebitCredit() {
    initAccount(AccountType.LIABILITY);

    int amountToCredit = 1000;
    Account.CreditInstruction creditInstruction = creditInstruction(amountToCredit);
    Account.AccountSummary accountSummary = accountClient.credit(creditInstruction);
    assertSummaryAndCurrentBalances(accountSummary, amountToCredit, 0);

    int amountToDebit = 750;
    Account.DebitInstruction debitInstruction = debitInstruction(amountToDebit);
    accountSummary = accountClient.debit(debitInstruction);
    int expectedBalance = amountToCredit - amountToDebit;
    assertSummaryAndCurrentBalances(accountSummary, expectedBalance, 0);
  }

  @Test
  public void assetAccountDebitCredit() {
    initAccount(AccountType.ASSET);

    int amountToDebit = 1000;
    Account.DebitInstruction debitInstruction = debitInstruction(amountToDebit);
    Account.AccountSummary accountSummary = accountClient.debit(debitInstruction);
    assertSummaryAndCurrentBalances(accountSummary, amountToDebit, 0);

    int amountToCredit = 750;
    Account.CreditInstruction creditInstruction = creditInstruction(amountToCredit);
    accountSummary = accountClient.credit(creditInstruction);
    int expectedBalance = amountToDebit - amountToCredit;
    assertSummaryAndCurrentBalances(accountSummary, expectedBalance, 0);
  }

  @Test
  public void liabilityAccountHoldAndDebit() {
    initAccount(AccountType.LIABILITY);

    int initialBalance = 1000;
    Account.CreditInstruction creditInstruction = creditInstruction(initialBalance);
    accountClient.credit(creditInstruction);

    int amountToHold = 750;
    Account.HoldResult holdResult = accountClient.hold(holdInstruction(amountToHold));
    assertSummaryAndCurrentBalances(
        holdResult.accountSummary(), initialBalance - amountToHold, amountToHold);
    Account.HoldSummary holdSummary = holdResult.holdSummary();
    assertSummaryAndCurrentBalances(holdSummary, amountToHold);

    int amountToDebit = 525;
    Account.AccountSummary accountSummary =
        accountClient.debit(debitInstruction(amountToDebit, holdSummary.holdId()));
    assertSummaryAndCurrentBalances(
        accountSummary, initialBalance - amountToHold, amountToHold - amountToDebit);
    assertCurrentHoldBalance(holdSummary.holdId(), amountToHold - amountToDebit);
  }

  @Test
  public void liabilityAccountMultipleHoldsAndDebit() {
    initAccount(AccountType.LIABILITY);

    int initialBalance = 1000;
    Account.CreditInstruction creditInstruction = creditInstruction(initialBalance);
    accountClient.credit(creditInstruction);

    int amountToHold1 = 750;
    Account.HoldResult holdResult = accountClient.hold(holdInstruction(amountToHold1));
    assertSummaryAndCurrentBalances(
        holdResult.accountSummary(), initialBalance - amountToHold1, amountToHold1);
    assertSummaryAndCurrentBalances(holdResult.holdSummary(), amountToHold1);
    Account.HoldSummary holdSummary1 = holdResult.holdSummary();

    int amountToHold2 = 75;
    holdResult = accountClient.hold(holdInstruction(amountToHold2));
    int expectedAvailableBalance = initialBalance - amountToHold1 - amountToHold2;
    int totalHold = amountToHold1 + amountToHold2;
    assertSummaryAndCurrentBalances(
        holdResult.accountSummary(), expectedAvailableBalance, totalHold);
    assertSummaryAndCurrentBalances(holdResult.holdSummary(), amountToHold2);
    Account.HoldSummary holdSummary2 = holdResult.holdSummary();

    int amountToDebit = 525;
    Account.AccountSummary accountSummary =
        accountClient.debit(debitInstruction(amountToDebit, holdSummary1.holdId()));
    assertSummaryAndCurrentBalances(
        accountSummary, expectedAvailableBalance, totalHold - amountToDebit);
    assertCurrentHoldBalance(holdSummary1.holdId(), amountToHold1 - amountToDebit);
    assertCurrentHoldBalance(holdSummary2.holdId(), amountToHold2);
  }

  private Account.AccountSummary initAccount(AccountType accountType) {
    Account.InitInstruction instruction =
        new Account.InitInstruction(new Account.AccountOptions(accountType, TEST_CURRENCY));
    return accountClient.init(instruction);
  }

  private void assertSummaryAndCurrentBalances(
      Account.AccountSummary accountSummary, int availableBalance, int holdBalance) {
    assertAccountSummaryBalances(accountSummary, availableBalance, holdBalance);
    assertCurrentAccountBalances(availableBalance, holdBalance);
  }

  private void assertCurrentAccountBalances(int availableBalance, int holdBalance) {
    Account.AccountSummary accountSummary = accountClient.getSummary();
    assertAccountSummaryBalances(accountSummary, availableBalance, holdBalance);
  }

  private void assertAccountSummaryBalances(
      Account.AccountSummary accountSummary, int availableBalance, int holdBalance) {
    assertThat(accountSummary.balances().availableBalance().currency()).isEqualTo(TEST_CURRENCY);
    assertThat(accountSummary.balances().availableBalance().amountInMinorUnits())
        .isEqualTo(availableBalance);
    assertThat(accountSummary.balances().holdBalance().currency()).isEqualTo(TEST_CURRENCY);
    assertThat(accountSummary.balances().holdBalance().amountInMinorUnits()).isEqualTo(holdBalance);
  }

  private Account.DebitInstruction debitInstruction(int amountToDebit) {
    return debitInstruction(amountToDebit, Optional.empty());
  }

  private Account.DebitInstruction debitInstruction(int amountToDebit, String holdId) {
    return debitInstruction(amountToDebit, Optional.of(holdId));
  }

  private Account.DebitInstruction debitInstruction(int amountToDebit, Optional<String> holdId) {
    return new Account.DebitInstruction(
        new Money(TEST_CURRENCY, BigInteger.valueOf(amountToDebit)),
        new Account.DebitOptions(holdId, TransactionMetadataFactory.transactionMetadata()));
  }

  private Account.CreditInstruction creditInstruction(int amountToCredit) {
    return new Account.CreditInstruction(
        new Money(TEST_CURRENCY, BigInteger.valueOf(amountToCredit)),
        new Account.CreditOptions(
            Optional.empty(), TransactionMetadataFactory.transactionMetadata()));
  }

  private Account.HoldInstruction holdInstruction(int amountToHold) {
    return new Account.HoldInstruction(
        new Money(TEST_CURRENCY, BigInteger.valueOf(amountToHold)),
        new Account.HoldOptions(TransactionMetadataFactory.transactionMetadata()));
  }

  private void assertHoldBalance(Account.HoldSummary holdSummary, int balance) {
    assertThat(holdSummary.balance().currency()).isEqualTo(TEST_CURRENCY);
    assertThat(holdSummary.balance().amountInMinorUnits()).isEqualTo(balance);
  }

  private void assertCurrentHoldBalance(String holdId, int balance) {
    Account.HoldSummary holdSummary = accountClient.getHoldSummary(holdId);
    assertHoldBalance(holdSummary, balance);
  }

  private void assertSummaryAndCurrentBalances(Account.HoldSummary holdSummary, int balance) {
    assertHoldBalance(holdSummary, balance);
    assertCurrentHoldBalance(holdSummary.holdId(), balance);
  }
}
