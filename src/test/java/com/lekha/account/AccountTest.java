package com.lekha.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.lekha.money.Currency;
import com.lekha.money.Money;
import com.lekha.testsetup.BaseRestateTest;
import dev.restate.client.Client;
import dev.restate.client.IngressException;
import dev.restate.sdk.testing.RestateClient;
import java.math.BigInteger;
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
    Account.CreditResult creditResult = accountClient.credit(creditInstruction);
    Account.AccountSummary accountSummary = creditResult.accountSummary();
    assertSummaryAndCurrentBalances(accountSummary, amountToCredit, 0);

    int amountToDebit = 750;
    Account.DebitInstruction debitInstruction = debitInstruction(amountToDebit);
    Account.DebitResult debitResult = accountClient.debit(debitInstruction);
    accountSummary = debitResult.accountSummary();
    int expectedBalance = amountToCredit - amountToDebit;
    assertSummaryAndCurrentBalances(accountSummary, expectedBalance, 0);
  }

  @Test
  public void assetAccountDebitCredit() {
    initAccount(AccountType.ASSET);

    int amountToDebit = 1000;
    Account.DebitInstruction debitInstruction = debitInstruction(amountToDebit);
    Account.DebitResult debitResult = accountClient.debit(debitInstruction);
    Account.AccountSummary accountSummary = debitResult.accountSummary();
    assertSummaryAndCurrentBalances(accountSummary, amountToDebit, 0);

    int amountToCredit = 750;
    Account.CreditInstruction creditInstruction = creditInstruction(amountToCredit);
    Account.CreditResult creditResult = accountClient.credit(creditInstruction);
    accountSummary = creditResult.accountSummary();
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
    Account.DebitHoldResult debitHoldResult =
        accountClient.debitHold(debitFromHoldInstruction(holdSummary.holdId(), amountToDebit));
    assertSummaryAndCurrentBalances(
        debitHoldResult.accountSummary(),
        initialBalance - amountToHold,
        amountToHold - amountToDebit);
    assertSummaryAndCurrentBalances(debitHoldResult.holdSummary(), amountToHold - amountToDebit);
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
    int expectedAvailableBalanceAfterHold = initialBalance - amountToHold1 - amountToHold2;
    int totalHold = amountToHold1 + amountToHold2;
    assertSummaryAndCurrentBalances(
        holdResult.accountSummary(), expectedAvailableBalanceAfterHold, totalHold);
    assertSummaryAndCurrentBalances(holdResult.holdSummary(), amountToHold2);
    Account.HoldSummary holdSummary2 = holdResult.holdSummary();

    int amountToDebit = 525;
    Account.DebitHoldResult debitHoldResult =
        accountClient.debitHold(debitFromHoldInstruction(holdSummary1.holdId(), amountToDebit));
    assertSummaryAndCurrentBalances(
        debitHoldResult.accountSummary(),
        expectedAvailableBalanceAfterHold,
        totalHold - amountToDebit);
    assertSummaryAndCurrentBalances(debitHoldResult.holdSummary(), amountToHold1 - amountToDebit);
    assertCurrentHoldBalance(holdSummary2.holdId(), amountToHold2);

    Account.ReleaseHoldResult releaseHoldResult =
        accountClient.releaseHold(releaseHoldInstruction(holdSummary1.holdId()));
    assertThat(releaseHoldResult.releasedAmount().currency()).isEqualTo(TEST_CURRENCY);
    assertThat(releaseHoldResult.releasedAmount().amountInMinorUnits())
        .isEqualTo(amountToHold1 - amountToDebit);
    assertSummaryAndCurrentBalances(
        releaseHoldResult.accountSummary(),
        initialBalance - amountToHold2 - amountToDebit,
        amountToHold2);
  }

  @Test
  public void liabilityAccount_transactionalCredit_thenReleaseHold() {
    initAccount(AccountType.LIABILITY);

    String transactionId = UUID.randomUUID().toString();
    int amountToCredit = 563;
    Account.TransactionalCreditResult transactionalCreditResult =
        accountClient.transactionalCredit(
            transactionCreditInstruction(transactionId, amountToCredit));

    assertSummaryAndCurrentBalances(transactionalCreditResult.accountSummary(), 0, amountToCredit);
    assertSummaryAndCurrentBalances(
        transactionalCreditResult.transactionHoldSummary(), amountToCredit);

    Account.TransactionalReleaseHoldResult releaseHoldResult =
        accountClient.transactionReleaseHold(transactionalReleaseHoldInstruction(transactionId));
    assertSummaryAndCurrentBalances(releaseHoldResult.accountSummary(), amountToCredit, 0);
    assertHoldBalance(releaseHoldResult.transactionHoldSummary(), 0);
    assertHoldDoesNotExists(releaseHoldResult.transactionHoldSummary().holdId());
  }

  @Test
  public void liabilityAccount_transactionalDebit_noExistingTransactionalHold() {
    initAccount(AccountType.LIABILITY);

    int initialBalance = 1000;
    accountClient.credit(creditInstruction(initialBalance));

    String transactionId = UUID.randomUUID().toString();
    int amountToDebit = 563;
    Account.TransactionalDebitResult debitResult =
        accountClient.transactionalDebit(transactionDebitInstruction(transactionId, amountToDebit));
    assertSummaryAndCurrentBalances(
        debitResult.accountSummary(), initialBalance - amountToDebit, 0);
    assertHoldDoesNotExists(debitResult.transactionHoldSummary().holdId());
  }

  @Test
  public void liabilityAccount_transactionalDebit_withExistingTransactionalHoldCoveringDebit() {
    initAccount(AccountType.LIABILITY);

    int initialBalance = 1000;
    accountClient.credit(creditInstruction(initialBalance));

    String transactionId = UUID.randomUUID().toString();
    int transactionalHoldBalance = 750;
    accountClient.transactionalCredit(
        transactionCreditInstruction(transactionId, transactionalHoldBalance));

    int amountToDebit = 350;
    Account.TransactionalDebitResult debitResult =
        accountClient.transactionalDebit(transactionDebitInstruction(transactionId, amountToDebit));
    assertSummaryAndCurrentBalances(
        debitResult.accountSummary(), initialBalance, transactionalHoldBalance - amountToDebit);
    assertSummaryAndCurrentBalances(
        debitResult.transactionHoldSummary(), transactionalHoldBalance - amountToDebit);
  }

  @Test
  public void
      liabilityAccount_transactionalDebit_withExistingTransactionalHoldNotEnoughToCoverDEbit() {
    initAccount(AccountType.LIABILITY);

    int initialBalance = 1000;
    accountClient.credit(creditInstruction(initialBalance));

    String transactionId = UUID.randomUUID().toString();
    int transactionalHoldBalance = 750;
    accountClient.transactionalCredit(
        transactionCreditInstruction(transactionId, transactionalHoldBalance));

    int amountToDebit = 853;
    Account.TransactionalDebitResult debitResult =
        accountClient.transactionalDebit(transactionDebitInstruction(transactionId, amountToDebit));
    assertSummaryAndCurrentBalances(
        debitResult.accountSummary(),
        initialBalance - (amountToDebit - transactionalHoldBalance),
        0);
    assertHoldDoesNotExists(debitResult.transactionHoldSummary().holdId());
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
    return new Account.DebitInstruction(
        new Money(TEST_CURRENCY, BigInteger.valueOf(amountToDebit)),
        OperationMetadataFactory.createOperationMetadata());
  }

  private Account.CreditInstruction creditInstruction(int amountToCredit) {
    return new Account.CreditInstruction(
        new Money(TEST_CURRENCY, BigInteger.valueOf(amountToCredit)),
        OperationMetadataFactory.createOperationMetadata());
  }

  private Account.HoldInstruction holdInstruction(int amountToHold) {
    return new Account.HoldInstruction(
        UUID.randomUUID().toString(),
        new Money(TEST_CURRENCY, BigInteger.valueOf(amountToHold)),
        OperationMetadataFactory.createOperationMetadata());
  }

  private Account.ReleaseHoldInstruction releaseHoldInstruction(String holdId) {
    return new Account.ReleaseHoldInstruction(
        holdId, OperationMetadataFactory.createOperationMetadata());
  }

  private Account.TransactionalReleaseHoldInstruction transactionalReleaseHoldInstruction(
      String holdId) {
    return new Account.TransactionalReleaseHoldInstruction(
        holdId, OperationMetadataFactory.createOperationMetadata());
  }

  private Account.DebitHoldInstruction debitFromHoldInstruction(String holdId, int amountToDebit) {
    return new Account.DebitHoldInstruction(holdId, debitInstruction(amountToDebit));
  }

  private Account.TransactionalCreditInstruction transactionCreditInstruction(
      String transactionId, int amountToCredit) {
    return new Account.TransactionalCreditInstruction(
        transactionId, creditInstruction(amountToCredit));
  }

  private Account.TransactionalDebitInstruction transactionDebitInstruction(
      String transactionId, int amountToDebit) {
    return new Account.TransactionalDebitInstruction(
        transactionId, debitInstruction(amountToDebit));
  }

  private void assertHoldBalance(Account.HoldSummary holdSummary, int balance) {
    assertThat(holdSummary.balance().currency()).isEqualTo(TEST_CURRENCY);
    assertThat(holdSummary.balance().amountInMinorUnits()).isEqualTo(balance);
  }

  private void assertCurrentHoldBalance(String holdId, int balance) {
    Account.HoldSummary holdSummary = accountClient.getHoldSummary(holdId);
    assertHoldBalance(holdSummary, balance);
  }

  private void assertCurrentTransactionalHoldBalance(String holdId, int balance) {
    Account.HoldSummary holdSummary = accountClient.getTransactionalHoldSummary(holdId);
    assertHoldBalance(holdSummary, balance);
  }

  private void assertSummaryAndCurrentBalances(Account.HoldSummary holdSummary, int balance) {
    assertHoldBalance(holdSummary, balance);
    switch (holdSummary.holdType()) {
      case TRANSACTION -> assertCurrentTransactionalHoldBalance(holdSummary.holdId(), balance);
      case USER -> assertCurrentHoldBalance(holdSummary.holdId(), balance);
    }
  }

  private void assertHoldDoesNotExists(String holdId) {
    assertThatExceptionOfType(IngressException.class)
        .isThrownBy(() -> accountClient.getHoldSummary(holdId))
        .matches(e -> e.getStatusCode() == 500);
  }
}
