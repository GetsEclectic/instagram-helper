import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import nu.studer.gradle.jooq.JooqEdition
import java.util.Properties

plugins {
    kotlin("jvm") version "1.3.41"
    id("org.flywaydb.flyway") version "5.2.4"
    id("nu.studer.jooq")
}

group = "org.getseclectic"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.brunocvcunha.instagram4j:instagram4j:1.11")
    compile("javax.annotation:javax.annotation-api:1.3.2")
    compile("org.apache.logging.log4j:log4j-core:2.12.0")
    compile("org.apache.logging.log4j:log4j-api:2.12.0")

    compile("org.postgresql:postgresql:42.2.6")
    jooqRuntime("org.postgresql:postgresql:42.2.6")
    compile("org.jooq:jooq:3.11.11")

    testImplementation("org.junit.jupiter:junit-jupiter:5.5.1")
    testImplementation("io.mockk:mockk:1.9")
    testImplementation("org.assertj:assertj-core:3.11.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val dbConfig = Properties()
File("dbconfig.properties").inputStream().let { dbConfig.load(it) }
val dbUser: String = dbConfig.getProperty("db.user")
val dbPassword: String = dbConfig.getProperty("db.password")
val dbUrl: String = dbConfig.getProperty("db.url")

flyway {
    url = dbUrl
    user = dbUser
    password = dbPassword
}

jooq {
    version = "3.11.11"
    edition = JooqEdition.OSS
    "Instagram4K"(sourceSets["main"]) {
        jdbc {
            driver = "org.postgresql.Driver"
            url = dbUrl
            user = dbUser
            password = dbPassword
        }
        generator {
            name = "org.jooq.codegen.DefaultGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                includes = ".*"
                excludes = ""
            }
            generate {
                isDeprecated = false
                isRecords = false
                isImmutablePojos = false
                isFluentSetters = false
            }
            target {
                packageName = "org.jooq.instagram4k"
            }
            strategy {
                name = "org.jooq.codegen.DefaultGeneratorStrategy"
            }
        }
    }
}