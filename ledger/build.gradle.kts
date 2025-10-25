plugins {
  java
  application
  id("com.diffplug.spotless")
}

val restateVersion: String by rootProject.extra

dependencies {
  annotationProcessor("dev.restate:sdk-api-gen:$restateVersion")

  // Restate SDK
  implementation("dev.restate:sdk-java-http:$restateVersion")
  implementation("dev.restate:client:${restateVersion}")
  implementation("dev.restate:admin-client:${restateVersion}")

  // Serde
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.18.4")

  // Logging
  implementation("org.apache.logging.log4j:log4j-api:2.24.1")
  implementation("org.apache.logging.log4j:log4j-core:2.24.1")
  implementation("org.slf4j:slf4j-api:2.0.17")
  implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.1") // routes SLF4J -> Log4j2

  // Metrics
  implementation("io.micrometer:micrometer-registry-atlas:1.15.4")

  // testing
  testImplementation(platform("org.junit:junit-bom:5.11.3"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testImplementation("dev.restate:sdk-testing:${restateVersion}")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

// Set main class
application {
  if (project.hasProperty("mainClass")) {
    mainClass.set(project.property("mainClass") as String)
  } else {
    mainClass.set("com.lekha.AppMain")
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

tasks.test {
    useJUnitPlatform()
}
