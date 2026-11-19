plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.2"
}

group = "dev.pmtd"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.2"))
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.21")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform { excludeTags("postgres") }
    environment("SPRING_PROFILES_ACTIVE", "test")
}

tasks.register<Test>("postgresTest") {
    description = "Runs integration checks against an explicitly configured disposable PostgreSQL schema."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("postgres") }
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    doFirst {
        val required = listOf("HOST", "PORT", "NAME", "USERNAME", "PASSWORD", "SCHEMA", "SSLMODE")
        check(required.all { !System.getenv("PMTD_TEST_DB_$it").isNullOrBlank() }) {
            "postgresTest requires all PMTD_TEST_DB_* variables; use only an authorized disposable database/schema."
        }
        check(System.getenv("PMTD_TEST_DB_SCHEMA").matches(Regex("pmtd_test_[a-z0-9_]{1,53}"))) {
            "PMTD_TEST_DB_SCHEMA must start with pmtd_test_ and contain only lowercase letters, digits and underscores."
        }
    }
}
