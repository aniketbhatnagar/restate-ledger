plugins {
  id("com.diffplug.spotless") version "6.25.0" apply false
}

extra["restateVersion"] = "2.4.0"

subprojects {
  repositories {
    mavenCentral()
  }
}
