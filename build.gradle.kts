group = "no.nav.syfo"
version = "0.0.1"

val FLYWAY = "10.17.2"
val HIKARI = "5.1.0"
val POSTGRES = "42.7.4"
val POSTGRES_EMBEDDED = "2.0.7"
val LOGBACK = "1.5.7"
val LOGSTASH_ENCODER = "7.4"
val MICROMETER_REGISTRY = "1.12.7"
val JACKSON_DATATYPE = "2.17.2"
val KAFKA = "3.7.0"
val KTOR = "3.0.1"
val MQ = "9.4.0.0"
val SPEK = "2.0.19"
val MOCKK = "1.13.12"
val NIMBUS_JOSE_JWT = "9.40"
val KLUENT = "1.73"

plugins {
    kotlin("jvm") version "2.0.20"
    id("com.gradleup.shadow") version "8.3.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-client-apache:$KTOR")
    implementation("io.ktor:ktor-client-content-negotiation:$KTOR")
    implementation("io.ktor:ktor-serialization-jackson:$KTOR")
    implementation("io.ktor:ktor-server-auth-jwt:$KTOR")
    implementation("io.ktor:ktor-server-call-id:$KTOR")
    implementation("io.ktor:ktor-server-content-negotiation:$KTOR")
    implementation("io.ktor:ktor-server-netty:$KTOR")
    implementation("io.ktor:ktor-server-status-pages:$KTOR")

    // Logging
    implementation("ch.qos.logback:logback-classic:$LOGBACK")
    implementation("net.logstash.logback:logstash-logback-encoder:$LOGSTASH_ENCODER")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$KTOR")
    implementation("io.micrometer:micrometer-registry-prometheus:$MICROMETER_REGISTRY")

    // Database
    implementation("org.postgresql:postgresql:$POSTGRES")
    implementation("com.zaxxer:HikariCP:$HIKARI")
    implementation("org.flywaydb:flyway-database-postgresql:$FLYWAY")
    testImplementation("io.zonky.test:embedded-postgres:$POSTGRES_EMBEDDED")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:$KAFKA", excludeLog4j)

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$JACKSON_DATATYPE")

    // MQ
    implementation("com.ibm.mq:com.ibm.mq.allclient:$MQ")

    // Tests
    testImplementation("io.ktor:ktor-server-test-host:$KTOR")
    testImplementation("io.mockk:mockk:$MOCKK")
    testImplementation("io.ktor:ktor-client-mock:$KTOR")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$NIMBUS_JOSE_JWT")
    testImplementation("org.amshove.kluent:kluent:$KLUENT")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$SPEK")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$SPEK")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
