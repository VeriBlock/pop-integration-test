import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
}

group = "org.veriblock"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("io.ktor:ktor-client:1.6.4")
    implementation("io.ktor:ktor-client-core-jvm:1.6.4")
    implementation("io.ktor:ktor-client-cio:1.6.4")
    implementation("io.ktor:ktor-client-auth:1.6.4")
    implementation("io.ktor:ktor-client-gson:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.3.0")
    implementation("org.testcontainers:testcontainers:1.16.0")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.github.veriblock.nodecore:nodecore-grpc:v0.4.13-rc.2")
    implementation("com.github.veriblock.nodecore:veriblock-extensions:v0.4.13-rc.2")
    implementation("com.github.veriblock.nodecore:vpm-mock:v0.4.13-rc.2")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.10")
    implementation("io.kotest:kotest-assertions-core-jvm:4.6.3")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}