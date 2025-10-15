package com.lekha.testsetup;

import com.lekha.accounts.Account;
import com.lekha.ledger.Ledger;
import com.lekha.transactions.Transfer;
import dev.restate.client.Client;
import dev.restate.sdk.testing.BindService;
import dev.restate.sdk.testing.RestateClient;
import dev.restate.sdk.testing.RestateTest;
import org.junit.jupiter.api.BeforeEach;

@RestateTest
public class BaseRestateTest {

  @BindService protected Account account = new Account();

  @BindService protected Ledger ledger = new Ledger();

  @BindService protected Transfer transfer = new Transfer();

  protected Client ingressClient;

  @BeforeEach
  public void setup(@RestateClient Client ingressClient) {
    this.ingressClient = ingressClient;
  }
}
