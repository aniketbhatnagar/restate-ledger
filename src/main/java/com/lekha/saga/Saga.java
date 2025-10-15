package com.lekha.saga;

import dev.restate.sdk.common.TerminalException;
import java.util.ArrayList;
import java.util.List;

public class Saga {

  @FunctionalInterface
  public interface WorkflowRunnable {
    void run() throws TerminalException;
  }

  @FunctionalInterface
  public interface WorkflowSupplier<T> {
    T supply() throws TerminalException;
  }

  private final List<WorkflowRunnable> compensations = new ArrayList<>();

  public <T> T run(WorkflowSupplier<T> task, WorkflowRunnable compensation) {
    try {
      T result = task.supply();
      this.compensations.add(compensation);
      return result;
    } catch (TerminalException e) {
      this.compensate();
      throw e;
    }
  }

  public void run(WorkflowRunnable task, WorkflowRunnable compensation) {
    try {
      task.run();
      this.compensations.add(compensation);
    } catch (TerminalException e) {
      this.compensate();
      throw e;
    }
  }

  public void compensate() {
    // run compensations in reverse order
    for (int i = compensations.size() - 1; i >= 0; i--) {
      compensations.get(i).run();
    }
  }
}
