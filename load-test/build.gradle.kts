plugins {
  java
  application
  id("com.diffplug.spotless")
}

dependencies {
  implementation(project(":ledger"))
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

application {
  mainClass.set("com.lekha.loadtest.LoadTestMain")
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

tasks.test {
  useJUnitPlatform()
}
