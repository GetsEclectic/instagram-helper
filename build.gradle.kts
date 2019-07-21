import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.11"
}

group = "ben"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.brunocvcunha.instagram4j:instagram4j:1.11")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}