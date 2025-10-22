plugins {
  java
  id("io.gatling.gradle") version "3.10.5"
  id("com.diffplug.spotless")
}

val gatlingVersion = "3.10.5"
val restateVersion: String by rootProject.extra

dependencies {
  gatlingImplementation(project(":ledger"))
  gatlingImplementation("dev.restate:client:$restateVersion")
  gatlingImplementation("dev.restate:sdk-java-http:$restateVersion")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

spotless {
  java {
    googleJavaFormat()
    importOrder()
    removeUnusedImports()
    formatAnnotations()
    toggleOffOn("//", "/n")
  }
}
