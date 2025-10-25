plugins {
  java
  application
  id("com.diffplug.spotless")
}

val gatlingVersion = "3.10.5"
val restateVersion: String by rootProject.extra

dependencies {
  implementation(project(":ledger"))
  implementation("dev.restate:client:$restateVersion")
  implementation("dev.restate:sdk-java-http:$restateVersion")

  implementation("io.gatling:gatling-core-java:$gatlingVersion")
  implementation("io.gatling:gatling-http-java:$gatlingVersion")
  implementation("io.gatling:gatling-app:$gatlingVersion")
  implementation("io.gatling.highcharts:gatling-charts-highcharts:$gatlingVersion")
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

application {
  mainClass.set("com.lekha.loadtest.LoadTestApp")
}
