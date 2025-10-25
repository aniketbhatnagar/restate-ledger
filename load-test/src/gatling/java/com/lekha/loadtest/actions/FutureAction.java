package com.lekha.loadtest.actions;

import com.typesafe.scalalogging.Logger;
import io.gatling.core.action.Action;
import io.gatling.javaapi.core.Session;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class FutureAction implements Action {
  private final Action next;
  private final Function<Session, CompletionStage<Session>> run;
  private Logger logger;

  public FutureAction(Action next, Function<Session, CompletionStage<Session>> run) {
    this.next = next;
    this.run = run;
  }

  @Override
  public String name() {
    return "FutureAction";
  }

  @Override
  public void execute(io.gatling.core.session.Session scalaSession) {
    run.apply(new Session(scalaSession))
        .whenComplete(
            (updated, error) -> {
              try (var eventLoop = scalaSession.eventLoop()) {
                eventLoop.execute(
                    () -> {
                      Session updatedSession =
                          (error == null) ? updated : new Session(scalaSession.markAsFailed());
                      next.execute(updatedSession.asScala());
                    });
              }
            });
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
