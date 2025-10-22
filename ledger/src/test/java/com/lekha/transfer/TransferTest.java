package com.lekha.transfer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.lekha.account.Account;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

  enum BulkMoveType {
    NON_TRANSACTIONAL,
    TRANSACTIONAL
  }

  @ParameterizedTest
  @EnumSource(BulkMoveType.class)
  public void bulkMove_betweenAssetAndLiability(BulkMoveType bulkMoveType) {
    String assetAccountId = UUID.randomUUID() + "-asset-1";
    String liabilityAccountId1 = UUID.randomUUID() + "-liability-1";
    AccountHelper assetAccount =
        AccountHelper.newUSDAssetAccountHelper(ingressClient, assetAccountId);
    AccountHelper liabilityAccount1 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId1);

    List<Transfer.MoveMoneyInstruction> moveMoneyInstructions =
        List.of(
            new Transfer.MoveMoneyInstruction(
                assetAccountId,
                liabilityAccountId1,
                new Money(Currency.USD, BigInteger.valueOf(1000L)),
                moveMoneyInstructionOptions()));

    executeBulkMove(bulkMoveType, moveMoneyInstructions);

    assetAccount.assertAvailableBalance(1000);
    liabilityAccount1.assertAvailableBalance(1000);
  }

  @ParameterizedTest
  @EnumSource(BulkMoveType.class)
  public void bulkMove_fromAssetToLiabilitiesChain(BulkMoveType bulkMoveType) {
    String assetAccountId = UUID.randomUUID() + "-asset-1";
    String liabilityAccountId1 = UUID.randomUUID() + "-liability-1";
    String liabilityAccountId2 = UUID.randomUUID() + "-liability-2";
    String liabilityAccountId3 = UUID.randomUUID() + "-liability-3";
    AccountHelper assetAccount =
        AccountHelper.newUSDAssetAccountHelper(ingressClient, assetAccountId);
    AccountHelper liabilityAccount1 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId1);
    AccountHelper liabilityAccount2 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId2);
    AccountHelper liabilityAccount3 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId3);

    List<Transfer.MoveMoneyInstruction> moveMoneyInstructions =
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
                moveMoneyInstructionOptions()),
            new Transfer.MoveMoneyInstruction(
                liabilityAccountId3,
                assetAccountId,
                new Money(Currency.USD, BigInteger.valueOf(15L)),
                moveMoneyInstructionOptions()));

    executeBulkMove(bulkMoveType, moveMoneyInstructions);

    assetAccount.assertAvailableBalance(985);
    liabilityAccount1.assertAvailableBalance(250);
    liabilityAccount2.assertAvailableBalance(700);
    liabilityAccount3.assertAvailableBalance(35);
  }

  @ParameterizedTest
  @EnumSource(BulkMoveType.class)
  public void bulkMove_oneMovementFails_resultsInRollback(BulkMoveType bulkMoveType) {
    String assetAccountId = UUID.randomUUID() + "-asset-1";
    String liabilityAccountId1 = UUID.randomUUID() + "-liability-1";
    String liabilityAccountId2 = UUID.randomUUID() + "-liability-2";
    String liabilityAccountId3 = UUID.randomUUID() + "-liability-3";
    AccountHelper assetAccount =
        AccountHelper.newUSDAssetAccountHelper(ingressClient, assetAccountId);
    AccountHelper liabilityAccount1 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId1);
    AccountHelper liabilityAccount2 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId2);
    AccountHelper liabilityAccount3 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId3);

    List<Transfer.MoveMoneyInstruction> moveMoneyInstructions =
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
                assetAccountId,
                new Money(Currency.USD, BigInteger.valueOf(180L)),
                moveMoneyInstructionOptions()),
            // Should fail
            new Transfer.MoveMoneyInstruction(
                liabilityAccountId2,
                liabilityAccountId3,
                new Money(Currency.USD, BigInteger.valueOf(800L)),
                moveMoneyInstructionOptions()));
    assertThatExceptionOfType(IngressException.class)
        .isThrownBy(
            () -> {
              executeBulkMove(bulkMoveType, moveMoneyInstructions);
            })
        .matches(e -> e.getStatusCode() == 500);

    assetAccount.assertAvailableBalance(0);
    liabilityAccount1.assertAvailableBalance(0);
    liabilityAccount2.assertAvailableBalance(0);
    liabilityAccount3.assertAvailableBalance(0);
  }

  @ParameterizedTest
  @EnumSource(BulkMoveType.class)
  public void bulkMove_withHold_oneMovementFails_resultsInRollback(BulkMoveType bulkMoveType) {
    String assetAccountId = UUID.randomUUID() + "-asset-1";
    String liabilityAccountId1 = UUID.randomUUID() + "-liability-1";
    String liabilityAccountId2 = UUID.randomUUID() + "-liability-2";
    String liabilityAccountId3 = UUID.randomUUID() + "-liability-3";
    AccountHelper assetAccount =
        AccountHelper.newUSDAssetAccountHelper(ingressClient, assetAccountId);
    AccountHelper liabilityAccount1 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId1);
    AccountHelper liabilityAccount2 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId2);
    AccountHelper liabilityAccount3 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId3);

    transferClient.move(
        new Transfer.MoveMoneyInstruction(
            assetAccountId,
            liabilityAccountId1,
            new Money(Currency.USD, BigInteger.valueOf(1000L)),
            moveMoneyInstructionOptions()));
    liabilityAccount1.hold(625);

    List<Transfer.MoveMoneyInstruction> moveMoneyInstructions =
        List.of(
            new Transfer.MoveMoneyInstruction(
                liabilityAccountId1,
                liabilityAccountId2,
                new Money(Currency.USD, BigInteger.valueOf(750L)),
                moveMoneyInstructionOptions()),
            new Transfer.MoveMoneyInstruction(
                liabilityAccountId2,
                assetAccountId,
                new Money(Currency.USD, BigInteger.valueOf(180L)),
                moveMoneyInstructionOptions()),
            // Should fail
            new Transfer.MoveMoneyInstruction(
                liabilityAccountId2,
                liabilityAccountId3,
                new Money(Currency.USD, BigInteger.valueOf(800L)),
                moveMoneyInstructionOptions()));
    assertThatExceptionOfType(IngressException.class)
        .isThrownBy(
            () -> {
              executeBulkMove(bulkMoveType, moveMoneyInstructions);
            })
        .matches(e -> e.getStatusCode() == 500);

    assetAccount.assertAvailableBalance(1000);
    liabilityAccount1.assertHoldBalance(625);
    liabilityAccount1.assertAvailableBalance(1000 - 625);
    liabilityAccount2.assertAvailableBalance(0);
    liabilityAccount3.assertAvailableBalance(0);
  }

  @ParameterizedTest
  @EnumSource(BulkMoveType.class)
  public void bulkMove_orderPartialFillScenario(BulkMoveType bulkMoveType) {
    String assetAccountId1 = UUID.randomUUID() + "-asset-1";
    String assetAccountId2 = UUID.randomUUID() + "-asset-2";
    String liabilityAccountId1 = UUID.randomUUID() + "-liability-1";
    String liabilityAccountId2 = UUID.randomUUID() + "-liability-1";
    AccountHelper assetAccount1 =
        AccountHelper.newUSDAssetAccountHelper(ingressClient, assetAccountId1);
    AccountHelper assetAccount2 =
        AccountHelper.newUSDAssetAccountHelper(ingressClient, assetAccountId2);
    AccountHelper liabilityAccount1 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId1);
    AccountHelper liabilityAccount2 =
        AccountHelper.newUSDLiabilityAccountHelper(ingressClient, liabilityAccountId2);

    int initialBalance = 1000;
    transferClient.move(
        new Transfer.MoveMoneyInstruction(
            assetAccountId1,
            liabilityAccountId1,
            new Money(Currency.USD, BigInteger.valueOf(initialBalance)),
            moveMoneyInstructionOptions()));

    int orderAmount = 750;
    Account.HoldResult holdResult = liabilityAccount1.hold(orderAmount);
    String holdId = holdResult.holdSummary().holdId();
    liabilityAccount1.assertAvailableBalance(initialBalance - orderAmount);
    liabilityAccount1.assertHoldBalance(orderAmount);

    int[] orderFills = new int[] {10, 25, 55, 75};
    int totalOrderFills = Arrays.stream(orderFills).sum();
    List<Transfer.MoveMoneyInstruction> moveMoneyInstructions =
        Arrays.stream(orderFills)
            .boxed()
            .flatMap(
                fill ->
                    Stream.of(
                        new Transfer.MoveMoneyInstruction(
                            liabilityAccountId1,
                            assetAccountId1,
                            new Money(Currency.USD, BigInteger.valueOf(fill)),
                            moveMoneyInstructionOptions(holdId)),
                        new Transfer.MoveMoneyInstruction(
                            assetAccountId2,
                            liabilityAccountId2,
                            new Money(Currency.USD, BigInteger.valueOf(fill)),
                            moveMoneyInstructionOptions())))
            .toList();
    executeBulkMove(bulkMoveType, moveMoneyInstructions);

    liabilityAccount1.assertAvailableBalance(initialBalance - orderAmount);
    liabilityAccount1.assertHoldBalance(orderAmount - totalOrderFills);
    liabilityAccount2.assertAvailableBalance(totalOrderFills);
    liabilityAccount2.assertHoldBalance(0);
    assetAccount1.assertAvailableBalance(initialBalance - totalOrderFills);
    assetAccount2.assertAvailableBalance(totalOrderFills);

    Account.ReleaseHoldResult releaseHoldResult = liabilityAccount1.releaseHold(holdId);
    assertThat(releaseHoldResult.releasedAmount().amountInMinorUnits())
        .isEqualTo(BigInteger.valueOf(orderAmount - totalOrderFills));
    liabilityAccount1.assertAvailableBalance(initialBalance - totalOrderFills);
    liabilityAccount1.assertHoldBalance(0);
    assetAccount1.assertAvailableBalance(initialBalance - totalOrderFills);
  }

  private void executeBulkMove(
      BulkMoveType bulkMoveType, List<Transfer.MoveMoneyInstruction> moveMoneyInstructions) {
    switch (bulkMoveType) {
      case NON_TRANSACTIONAL -> transferClient.bulkMove(moveMoneyInstructions);
      case TRANSACTIONAL -> transferClient.transactionalBulkMove(moveMoneyInstructions);
    }
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
