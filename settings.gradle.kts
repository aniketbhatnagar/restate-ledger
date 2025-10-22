plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

rootProject.name = "restate-ledger"

include("ledger", "load-test")
