plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// HU-05 (#12): cobertura de líneas mínima del 60 % en este servicio núcleo.
// JaCoCo se aplica en el build.gradle.kts raíz; aquí solo se exige el umbral.
// Corre al terminar `test`, así que el pipeline falla si la cobertura baja.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}
tasks.named<Test>("test") { finalizedBy("jacocoTestCoverageVerification") }
tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
