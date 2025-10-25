package com.lekha;

import com.lekha.account.Account;
import com.lekha.ledger.Ledger;
import com.lekha.transfer.Transfer;
import dev.restate.admin.api.DeploymentApi;
import dev.restate.admin.client.ApiClient;
import dev.restate.admin.model.RegisterDeploymentRequest;
import dev.restate.admin.model.RegisterDeploymentRequestAnyOf;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AppMain {
  private static final Logger LOG = LogManager.getLogger(AppMain.class);

  public static void main(String[] args) throws Exception {
    RestateHttpServer.listen(Endpoint.bind(new Account()).bind(new Ledger()).bind(new Transfer()));
    LOG.info("App started");

    ApiClient adminApiClient = new ApiClient();
    adminApiClient.setHost("runtime");
    adminApiClient.setPort(9070);
    DeploymentApi deploymentApi = new DeploymentApi(adminApiClient);
    RegisterDeploymentRequestAnyOf registerRequest = new RegisterDeploymentRequestAnyOf();
    registerRequest.uri("http://devcontainer:9080");
    RegisterDeploymentRequest deploymentRequest = new RegisterDeploymentRequest(registerRequest);
    deploymentApi.createDeployment(deploymentRequest);
    LOG.info("App registered");
  }
}
