package com.lekha.loadtest;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import com.lekha.loadtest.actions.FutureActionBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

public class MoveBetweenAccountsSimulation extends Simulation {

  private static final LoadTestSettings SETTINGS = LoadTestSettings.fromEnvironment();

  private final AtomicReference<LedgerLoadTestContext> contextRef = new AtomicReference<>();

  {
    ScenarioBuilder moveScenario =
        scenario("move-between-accounts")
            .forever()
            .on(exec(new FutureActionBuilder("move-funds", this::executeMove)));

    Duration duration = SETTINGS.runDuration();
    setUp(
            moveScenario.injectClosed(
                constantConcurrentUsers(SETTINGS.concurrentUsers()).during(duration)))
        .maxDuration(duration);
  }

  public void before() {
    LedgerLoadTestContext context = LedgerLoadTestContext.initialize(SETTINGS);
    contextRef.set(context);
  }

  public void after() {
    LedgerLoadTestContext context = contextRef.get();
    if (context != null) {
      context.shutdown();
    }
  }

  private CompletionStage<Session> executeMove(Session session) {
    LedgerLoadTestContext context = contextRef.get();
    if (context == null) {
      return CompletableFuture.completedFuture(session.markAsFailed());
    }
    return context
        .moveFundsToRandomLiability()
        .thenApply(__ -> session)
        .exceptionally(
            throwable -> {
              Session failedSession = session.markAsFailed();
              String message = throwable.getMessage();
              if (message != null) {
                failedSession = failedSession.set("lastError", message);
              }
              return failedSession;
            });
  }
}
