package com.lekha.transactions;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.lekha.accounts.Account;
import com.lekha.money.Currency;
import com.lekha.money.Money;
import com.lekha.testsetup.AccountHelper;
import com.lekha.testsetup.BaseRestateTest;
import dev.restate.client.Client;
import dev.restate.client.IngressException;
import dev.restate.sdk.testing.RestateClient;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransferTest extends BaseRestateTest {

  private TransferClient.IngressClient transferClient;

  @BeforeEach
  public void setup(@RestateClient Client ingressClient) {
    super.setup(ingressClient);
    transferClient = TransferClient.fromClient(ingressClient);
  }

  @Test
  public void move_betweenAssetAndLiability() {
    String assetAccountId = UUID.randomUUID() + "-asset-1";
    String liabilityAccountId = UUID.randomUUID() + "-liability-1";
    AccountHelper assetAccount =
        AccountHelper.newUSDAssetAccountHelper(this.ingressClient, assetAccountId);
    AccountHelper liabilityAccount =
        AccountHelper.newUSDLiabilityAccountHelper(this.ingressClient, liabilityAccountId);

    transferClient.move(
        new Transfer.MoveMoneyInstruction(
            assetAccountId,
            liabilityAccountId,
            new Money(Currency.USD, BigInteger.valueOf(1000L)),
            moveMoneyInstructionOptions()));

    assetAccount.assertAvailableBalance(1000);
    liabilityAccount.assertAvailableBalance(1000);
  }

  @Test
  public void bulkMove_fromAssetToLiabilitiesChain() {
    String assetAccountId = UUID.randomUUID() + "-asset-1";
    String liabilityAccountId1 = UUID.randomUUID() + "-liability-1";
    String liabilityAccountId2 = UUID.randomUUID() + "-liability-2";
    String liabilityAccountId3 = UUID.randomUUID() + "-liability-3";
    AccountHelper assetAccount =
        AccountHelper.newUSDAssetAccountHelper(this.ingressClient, assetAccountId);
    AccountHelper liabilityAccount1 =
        AccountHelper.newUSDLiabilityAccountHelper(this.ingressClient, liabilityAccountId1);
    AccountHelper liabilityAccount2 =
        AccountHelper.newUSDLiabilityAccountHelper(this.ingressClient, liabilityAccountId2);
    AccountHelper liabilityAccount3 =
        AccountHelper.newUSDLiabilityAccountHelper(this.ingressClient, liabilityAccountId3);

    transferClient.bulkMove(
        List.of(
            new Transfer.MoveMoneyInstruction(
                assetAccountId,
                liabilityAccountId1,
                new Money(Currency.USD, BigInteger.valueOf(1000L)),
                moveMoneyInstructionOptions()),
            new Transfer.MoveMoneyInstruction(
                liabilityAccountId1,
                liabilityAccountId2,
                new Money(Currency.USD, BigInteger.valueOf(750L)),
                moveMoneyInstructionOptions()),
            new Transfer.MoveMoneyInstruction(
                liabilityAccountId2,
                liabilityAccountId3,
                new Money(Currency.USD, BigInteger.valueOf(50L)),
                moveMoneyInstructionOptions())));

    assetAccount.assertAvailableBalance(1000);
    liabilityAccount1.assertAvailableBalance(250);
    liabilityAccount2.assertAvailableBalance(700);
    liabilityAccount3.assertAvailableBalance(50);
  }

  @Test
  public void bulkMove_OneMovementFails_resultsInRollback() {
    String assetAccountId = UUID.randomUUID() + "-asset-1";
    String liabilityAccountId1 = UUID.randomUUID() + "-liability-1";
    String liabilityAccountId2 = UUID.randomUUID() + "-liability-2";
    String liabilityAccountId3 = UUID.randomUUID() + "-liability-3";
    AccountHelper assetAccount =
        AccountHelper.newUSDAssetAccountHelper(this.ingressClient, assetAccountId);
    AccountHelper liabilityAccount1 =
        AccountHelper.newUSDLiabilityAccountHelper(this.ingressClient, liabilityAccountId1);
    AccountHelper liabilityAccount2 =
        AccountHelper.newUSDLiabilityAccountHelper(this.ingressClient, liabilityAccountId2);
    AccountHelper liabilityAccount3 =
        AccountHelper.newUSDLiabilityAccountHelper(this.ingressClient, liabilityAccountId3);

    assertThatExceptionOfType(IngressException.class)
        .isThrownBy(
            () -> {
              transferClient.bulkMove(
                  List.of(
                      new Transfer.MoveMoneyInstruction(
                          assetAccountId,
                          liabilityAccountId1,
                          new Money(Currency.USD, BigInteger.valueOf(1000L)),
                          moveMoneyInstructionOptions()),
                      new Transfer.MoveMoneyInstruction(
                          liabilityAccountId1,
                          liabilityAccountId2,
                          new Money(Currency.USD, BigInteger.valueOf(750L)),
                          moveMoneyInstructionOptions()),
                      // Should fail
                      new Transfer.MoveMoneyInstruction(
                          liabilityAccountId2,
                          liabilityAccountId3,
                          new Money(Currency.USD, BigInteger.valueOf(800L)),
                          moveMoneyInstructionOptions())));
            })
        .matches(e -> e.getStatusCode() == 500);

    assetAccount.assertAvailableBalance(0);
    liabilityAccount1.assertAvailableBalance(0);
    liabilityAccount2.assertAvailableBalance(0);
    liabilityAccount3.assertAvailableBalance(0);
  }

  @Test
  public void bulkMove_orderPartialFillScenario() {
    String assetAccountId = UUID.randomUUID() + "-asset-1";
    String liabilityAccountId = UUID.randomUUID() + "-liability-1";
    AccountHelper assetAccount =
        AccountHelper.newUSDAssetAccountHelper(this.ingressClient, assetAccountId);
    AccountHelper liabilityAccount =
        AccountHelper.newUSDLiabilityAccountHelper(this.ingressClient, liabilityAccountId);

    int initialBalance = 1000;
    transferClient.bulkMove(
        List.of(
            new Transfer.MoveMoneyInstruction(
                assetAccountId,
                liabilityAccountId,
                new Money(Currency.USD, BigInteger.valueOf(initialBalance)),
                moveMoneyInstructionOptions())));

    int orderAmount = 750;
    Account.HoldResult holdResult = liabilityAccount.hold(orderAmount);
    String holdId = holdResult.holdSummary().holdId();
    liabilityAccount.assertAvailableBalance(initialBalance - orderAmount);
    liabilityAccount.assertHoldBalance(orderAmount);

    int[] orderFills = new int[] {10, 25, 55, 75};
    int totalOrderFills = Arrays.stream(orderFills).sum();
    List<Transfer.MoveMoneyInstruction> moveMoneyInstructions =
        Arrays.stream(orderFills)
            .mapToObj(
                fill ->
                    new Transfer.MoveMoneyInstruction(
                        liabilityAccountId,
                        assetAccountId,
                        new Money(Currency.USD, BigInteger.valueOf(fill)),
                        moveMoneyInstructionOptions(holdId)))
            .toList();
    transferClient.bulkMove(moveMoneyInstructions);

    liabilityAccount.assertAvailableBalance(initialBalance - orderAmount);
    liabilityAccount.assertHoldBalance(orderAmount - totalOrderFills);
    assetAccount.assertAvailableBalance(initialBalance - totalOrderFills);

    Account.ReleaseHoldResult releaseHoldResult = liabilityAccount.releaseHold(holdId);
    assertThat(releaseHoldResult.releasedAmount().amountInMinorUnits())
        .isEqualTo(BigInteger.valueOf(orderAmount - totalOrderFills));
    liabilityAccount.assertAvailableBalance(initialBalance - totalOrderFills);
    liabilityAccount.assertHoldBalance(0);
    assetAccount.assertAvailableBalance(initialBalance - totalOrderFills);
  }

  private Transfer.MoveMoneyInstructionOptions moveMoneyInstructionOptions(String holdId) {
    return moveMoneyInstructionOptions(Optional.of(holdId));
  }

  private Transfer.MoveMoneyInstructionOptions moveMoneyInstructionOptions() {
    return moveMoneyInstructionOptions(Optional.empty());
  }

  private Transfer.MoveMoneyInstructionOptions moveMoneyInstructionOptions(
      Optional<String> holdId) {
    return new Transfer.MoveMoneyInstructionOptions(holdId);
  }
}
