package com.lekha.loadtest.actions;

import io.gatling.core.action.Action;
import io.gatling.core.structure.ScenarioContext;
import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class FutureActionBuilder implements ActionBuilder {
  private final String requestName;
  private final Function<Session, CompletionStage<Session>> run;

  public FutureActionBuilder(String requestName, Function<Session, CompletionStage<Session>> run) {
    this.requestName = requestName;
    this.run = run;
  }

  @Override
  public io.gatling.core.action.builder.ActionBuilder asScala() {
    return new io.gatling.core.action.builder.ActionBuilder() {
      @Override
      public Action build(ScenarioContext ctx, Action next) {
        return new FutureAction(
            next,
            ctx.coreComponents().statsEngine(),
            ctx.coreComponents().clock(),
            requestName,
            run);
      }
    };
  }
}
