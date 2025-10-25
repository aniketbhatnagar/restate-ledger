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
    adminApiClient.setHost(envOrDefault("RESTATE_ADMIN_HOST", "runtime"));
    adminApiClient.setPort(envIntOrDefault("RESTATE_ADMIN_PORT", 9070));
    DeploymentApi deploymentApi = new DeploymentApi(adminApiClient);
    RegisterDeploymentRequestAnyOf registerRequest = new RegisterDeploymentRequestAnyOf();
    registerRequest.uri(envOrDefault("LEDGER_PUBLIC_URI", "http://localhost:9080"));
    RegisterDeploymentRequest deploymentRequest = new RegisterDeploymentRequest(registerRequest);
    deploymentApi.createDeployment(deploymentRequest);
    LOG.info("App registered");
  }

  private static String envOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value;
  }

  private static int envIntOrDefault(String key, int defaultValue) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      LOG.warn("Invalid integer for {} ({}), using {}", key, value, defaultValue);
      return defaultValue;
    }
  }
}
