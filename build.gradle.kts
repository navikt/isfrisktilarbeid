group = "no.nav.syfo"
version = "0.0.1"

val CONFLUENT = "7.9.0"
val FLYWAY = "11.13.1"
val HIKARI = "6.3.0"
val POSTGRES = "42.7.8"
val POSTGRES_EMBEDDED = "2.1.1"
val POSTGRES_RUNTIME_VERSION = "17.6.0"
val LOGBACK = "1.5.18"
val LOGSTASH_ENCODER = "8.1"
val MICROMETER_REGISTRY = "1.12.13"
val JACKSON_DATATYPE = "2.20.0"
val KAFKA = "3.9.1"
val KTOR = "3.3.0"
val MQ = "9.4.3.0"
val SPEK = "2.0.19"
val MOCKK = "1.14.5"
val NIMBUS_JOSE_JWT = "10.5"
val KLUENT = "1.73"

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "8.3.6"
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
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:$POSTGRES_RUNTIME_VERSION"))

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:$KAFKA", excludeLog4j)
    constraints {
        implementation("org.apache.zookeeper:zookeeper") {
            because("org.apache.kafka:kafka_2.13:$KAFKA -> https://www.cve.org/CVERecord?id=CVE-2023-44981")
            version {
                require("3.9.4")
            }
        }
        implementation("commons-beanutils:commons-beanutils") {
            because("org.apache.kafka:kafka_2.13:$KAFKA -> https://www.cve.org/CVERecord?id=CVE-2025-48734")
            version {
                require("1.11.0")
            }
        }
    }

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$JACKSON_DATATYPE")

    // MQ
    implementation("com.ibm.mq:com.ibm.mq.allclient:$MQ")

    implementation("io.confluent:kafka-avro-serializer:$CONFLUENT", excludeLog4j)
    constraints {
        implementation("org.apache.avro:avro") {
            because("io.confluent:kafka-avro-serializer:$CONFLUENT -> https://www.cve.org/CVERecord?id=CVE-2023-39410")
            version {
                require("1.12.0")
            }
        }
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.27.1")
            }
        }
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-lang3:3.16.0 -> https://www.cve.org/CVERecord?id=CVE-2025-48924")
            version {
                require("3.19.0")
            }
        }
    }

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

        val failedTests = mutableListOf<String>()
        var passedCount = 0
        var failedCount = 0
        var skippedCount = 0

        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null) {
                    println("Test summary: $passedCount passed, $failedCount failed, $skippedCount skipped")
                    if (failedTests.isNotEmpty()) {
                        println("Failed tests:")
                        failedTests.forEach { println(" - $it") }
                    }
                }
            }
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                when (result.resultType) {
                    TestResult.ResultType.SUCCESS -> passedCount++
                    TestResult.ResultType.FAILURE -> {
                        failedCount++
                        failedTests.add(testDescriptor.displayName)
                    }
                    TestResult.ResultType.SKIPPED -> skippedCount++
                    else -> {}
                }
            }
        })
    }
}
