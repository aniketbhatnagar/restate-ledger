package com.lekha;

import com.lekha.account.Account;
import com.lekha.ledger.Ledger;
import com.lekha.transfer.Transfer;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AppMain {
  private static final Logger LOG = LogManager.getLogger(AppMain.class);

  public static void main(String[] args) {
    RestateHttpServer.listen(Endpoint.bind(new Account()).bind(new Ledger()).bind(new Transfer()));
    LOG.info("App started");
  }
}
