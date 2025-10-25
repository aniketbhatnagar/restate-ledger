package com.lekha.loadtest.actions;

import com.typesafe.scalalogging.Logger;
import io.gatling.commons.stats.KO$;
import io.gatling.commons.stats.OK$;
import io.gatling.commons.stats.Status;
import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import scala.Option;

public class FutureAction implements Action {
  private final Action next;
  private final Function<Session, CompletionStage<Session>> run;
  private final StatsEngine statsEngine;
  private final Clock clock;
  private final String requestName;
  private Logger logger;

  public FutureAction(
      Action next,
      StatsEngine statsEngine,
      Clock clock,
      String requestName,
      Function<Session, CompletionStage<Session>> run) {
    this.next = next;
    this.statsEngine = statsEngine;
    this.clock = clock;
    this.requestName = Objects.requireNonNull(requestName, "requestName");
    this.run = run;
  }

  @Override
  public String name() {
    return "FutureAction";
  }

  @Override
  public void execute(io.gatling.core.session.Session scalaSession) {
    long start = clock.nowMillis();
    CompletionStage<Session> futureSession;
    try {
      futureSession = run.apply(new Session(scalaSession));
    } catch (Throwable throwable) {
      handleCompletion(scalaSession, start, null, throwable);
      return;
    }

    futureSession.whenComplete(
        (updated, error) -> handleCompletion(scalaSession, start, updated, error));
  }

  private void handleCompletion(
      io.gatling.core.session.Session scalaSession, long start, Session updated, Throwable error) {
    scalaSession
        .eventLoop()
        .execute(
            () -> {
              long end = clock.nowMillis();
              Status status = error == null ? OK$.MODULE$ : KO$.MODULE$;
              String message = extractMessage(error);
              Option<String> responseCode = Option.empty();
              Option<String> messageOption = Option.apply(message);
              statsEngine.logResponse(
                  scalaSession.scenario(),
                  scalaSession.groups(),
                  requestName,
                  start,
                  end,
                  status,
                  responseCode,
                  messageOption);

              Session sessionToForward = updated != null ? updated : new Session(scalaSession);
              Session finalSession =
                  (error == null)
                      ? sessionToForward.markAsSucceeded()
                      : sessionToForward.markAsFailed();
              next.execute(finalSession.asScala());
            });
  }

  private String extractMessage(Throwable error) {
    if (error == null) {
      return null;
    }
    Throwable current = error;
    while (current.getCause() != null && current.getMessage() == null) {
      current = current.getCause();
    }
    return current.getMessage();
  }

  @Override
  public void com$typesafe$scalalogging$StrictLogging$_setter_$logger_$eq(Logger logger) {
    this.logger = logger;
  }

  @Override
  public Logger logger() {
    return logger;
  }
}
