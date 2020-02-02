import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import nu.studer.gradle.jooq.JooqEdition
import java.util.Properties
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.3.41"
    id("org.flywaydb.flyway") version "5.2.4"
    id("nu.studer.jooq")
}

group = "org.getseclectic"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
//    mavenLocal()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3")
    compile("org.brunocvcunha.instagram4j:instagram4j:1.12")
    compile("javax.annotation:javax.annotation-api:1.3.2")
    compile("org.apache.logging.log4j:log4j-core:2.12.0")
    compile("org.apache.logging.log4j:log4j-api:2.12.0")
    compile("com.google.code.gson:gson:2.8.5")
    compile("com.github.kittinunf.fuel:fuel:2.2.0")
    compile("org.apache.commons:commons-math3:3.6.1")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:0.7.3")

    compile("org.postgresql:postgresql:42.2.6")
    jooqRuntime("org.postgresql:postgresql:42.2.6")
    compile("org.jooq:jooq:3.12.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.5.1")
    testImplementation("io.mockk:mockk:1.9")
    testImplementation("org.assertj:assertj-core:3.11.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val config = Properties()
File("config.properties").inputStream().let { config.load(it) }
val dbUser: String = config.getProperty("db.user")
val dbPassword: String = config.getProperty("db.password")
val dbUrl: String = config.getProperty("db.url")

flyway {
    url = dbUrl
    user = dbUser
    password = dbPassword
}

jooq {
    version = "3.12.1"
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



val fatJar = task("fatJar", type = Jar::class) {
    baseName = "${project.name}-fat"
    manifest {
        attributes["Implementation-Title"] = "Instagram4K"
        attributes["Implementation-Version"] = version
        attributes["Main-Class"] = "DeployKt"
    }
    from(configurations.runtimeClasspath.get().map({ if (it.isDirectory) it else zipTree(it) }))
    with(tasks.jar.get() as CopySpec)
}