package com.lekha.loadtest;

import io.gatling.app.Gatling;
import io.gatling.core.config.GatlingPropertiesBuilder;
import io.gatling.javaapi.core.Simulation;
import java.util.Map;
import java.util.Optional;

/**
 * Entrypoint to run Gatling simulations as a regular Java application. This makes it easy to
 * containerize the load tests and execute them without the Gradle Gatling plugin.
 */
public final class LoadTestApp {

  private static final Map<String, Class<? extends Simulation>> KNOWN_SIMULATIONS =
      Map.of(
          MoveBetweenAccountsSimulation.class.getSimpleName(), MoveBetweenAccountsSimulation.class);

  public static void main(String[] args) {
    String simulationClassName = resolveSimulationClass(args);
    GatlingPropertiesBuilder props =
        new GatlingPropertiesBuilder().simulationClass(simulationClassName);

    Optional.ofNullable(System.getenv("LEDGER_LOAD_RESULTS_DIR"))
        .filter(value -> !value.isBlank())
        .ifPresent(props::resultsDirectory);

    Gatling.fromMap(props.build());
  }

  private static String resolveSimulationClass(String[] args) {
    String explicit = firstNonBlank(firstArg(args), System.getenv("LEDGER_LOAD_SIMULATION"));
    String requested =
        explicit != null ? explicit : MoveBetweenAccountsSimulation.class.getSimpleName();

    Class<? extends Simulation> simulation = KNOWN_SIMULATIONS.get(requested);
    if (simulation != null) {
      return simulation.getName();
    }

    // Allow fully-qualified class names so new simulations can be introduced without changing this
    // class immediately.
    try {
      Class<?> candidate = Class.forName(requested);
      if (Simulation.class.isAssignableFrom(candidate)) {
        return candidate.getName();
      }
      throw new IllegalArgumentException(
          "Class %s is not a Gatling Simulation".formatted(candidate.getName()));
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "Unknown simulation '%s'. Known values: %s"
              .formatted(requested, String.join(", ", KNOWN_SIMULATIONS.keySet())),
          e);
    }
  }

  private static String firstArg(String[] args) {
    if (args == null || args.length == 0) {
      return null;
    }
    return args[0];
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
