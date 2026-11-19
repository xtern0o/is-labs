plugins {
    kotlin("jvm") version "2.3.0"
    id("io.gatling.gradle") version "3.16.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}