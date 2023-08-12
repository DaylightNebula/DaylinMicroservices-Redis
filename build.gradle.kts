import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    `maven-publish`
    java
}

group = "io.github.daylightnebula"
version = "0.1.0"

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
    kotlinOptions.jvmTarget = "11"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String

            pom {
                url.set("https://github.com/DaylightNebula/DaylinMicroservices-Redis")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("DaylightNebula")
                        name.set("Noah Shaw")
                        email.set("noah.w.shaw@gmail.com")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/DaylightNebula/DaylinMicroservices-Redis")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}