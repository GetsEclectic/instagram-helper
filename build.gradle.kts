import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
    id("org.flywaydb.flyway") version "5.2.4"
}

group = "getseclectic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.brunocvcunha.instagram4j:instagram4j:1.11")
    compile("org.postgresql:postgresql:9.3-1100-jdbc4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.5.1")
    testImplementation("io.mockk:mockk:1.9")
    testImplementation("org.assertj:assertj-core:3.11.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val dbpassword = System.getenv("DBPASSWORD")

flyway {
    url = "jdbc:postgresql://localhost/instagram4k"
    user = "instagram4k_app"
    password = dbpassword
}