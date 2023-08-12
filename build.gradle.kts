import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // json
    implementation("org.json:json:20230618")

    // logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.6")

    // redis
    implementation("io.lettuce:lettuce-core:6.2.5.RELEASE")

    // daylin microservices
    implementation("io.github.daylightnebula.DaylinMicroservices:DaylinMicroservices-Core:0.4.4")
    implementation("io.github.daylightnebula.DaylinMicroservices:DaylinMicroservices-Serializables:0.4.4")

    // testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}