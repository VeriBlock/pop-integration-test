import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
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

val kotestVersion = "5.0.1"
val coroutinesVersion = "1.5.2-native-mt"
val log4jVersion= "2.16.0"
val ktorVersion = "1.6.6"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
    implementation("io.github.microutils:kotlin-logging:2.1.16")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("io.ktor:ktor-client:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.3.1")
    implementation("org.testcontainers:testcontainers:1.16.2")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.github.veriblock.nodecore:nodecore-grpc:v0.4.13-rc.2")
    implementation("com.github.veriblock.nodecore:veriblock-extensions:v0.4.13-rc.2")
    implementation("com.github.veriblock.nodecore:vpm-mock:v0.4.13-rc.2")
    implementation("io.github.microutils:kotlin-logging:2.1.16")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = SHORT
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
tasks.withType<JavaCompile> {
    targetCompatibility = "1.8"
    sourceCompatibility = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}
