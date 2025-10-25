package com.lekha.loadtest;

import com.lekha.account.Account;
import com.lekha.account.AccountClient;
import com.lekha.account.AccountType;
import com.lekha.money.Money;
import com.lekha.transfer.Transfer;
import com.lekha.transfer.TransferClient;
import dev.restate.client.Client;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;

final class LedgerLoadTestContext {

  private final Client client;
  private final TransferClient.IngressClient transferClient;
  private final String assetAccountId;
  private final List<String> liabilityAccountIds;
  private final Money transferAmount;
  private final Transfer.MoveMoneyInstructionOptions moveOptions;

  private LedgerLoadTestContext(
      Client client,
      TransferClient.IngressClient transferClient,
      String assetAccountId,
      List<String> liabilityAccountIds,
      Money transferAmount) {
    this.client = client;
    this.transferClient = transferClient;
    this.assetAccountId = assetAccountId;
    this.liabilityAccountIds = liabilityAccountIds;
    this.transferAmount = transferAmount;
    this.moveOptions = new Transfer.MoveMoneyInstructionOptions(Optional.empty());
  }

  static LedgerLoadTestContext initialize(LoadTestSettings settings) {
    Client client = Client.connect(settings.baseUri());
    TransferClient.IngressClient transferClient = TransferClient.fromClient(client);

    String assetAccountId = "asset-" + UUID.randomUUID();
    AccountClient.IngressClient asset = AccountClient.fromClient(client, assetAccountId);
    Account.InitInstruction assetInit =
        new Account.InitInstruction(
            new Account.AccountOptions(AccountType.ASSET, settings.transferAmount().currency()));

    List<CompletableFuture<?>> initFutures = new ArrayList<>();
    initFutures.add(asset.initAsync(assetInit));

    List<String> liabilityAccountIds = new ArrayList<>(settings.liabilityAccounts());
    for (int i = 0; i < settings.liabilityAccounts(); i++) {
      String accountId = "liability-" + i + "-" + UUID.randomUUID();
      AccountClient.IngressClient liability = AccountClient.fromClient(client, accountId);
      Account.InitInstruction liabilityInit =
          new Account.InitInstruction(
              new Account.AccountOptions(
                  AccountType.LIABILITY, settings.transferAmount().currency()));
      initFutures.add(liability.initAsync(liabilityInit));
      liabilityAccountIds.add(accountId);
    }

    try {
      CompletableFuture.allOf(initFutures.toArray(CompletableFuture[]::new)).join();
    } catch (RuntimeException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      throw new IllegalStateException("Failed to initialize ledger accounts for load test", cause);
    }

    return new LedgerLoadTestContext(
        client,
        transferClient,
        assetAccountId,
        List.copyOf(liabilityAccountIds),
        settings.transferAmount());
  }

  CompletionStage<Void> moveFundsToRandomLiability() {
    Objects.requireNonNull(transferAmount, "transferAmount");
    String liabilityAccountId =
        liabilityAccountIds.get(ThreadLocalRandom.current().nextInt(liabilityAccountIds.size()));
    Transfer.MoveMoneyInstruction instruction =
        new Transfer.MoveMoneyInstruction(
            assetAccountId, liabilityAccountId, transferAmount, moveOptions);
    return transferClient.moveAsync(instruction);
  }

  void shutdown() {
    // Restate client currently does not expose a close method; placeholder in case cleanup is
    // added.
  }
}
