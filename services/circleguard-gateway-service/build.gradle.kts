plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// HU-06 (#13): pruebas de integración con Testcontainers (@Tag("integration")).
// No corren en `test`; el pipeline ejecuta esta tarea en su etapa "Integration tests".
val integrationTest by tasks.registering(Test::class) {
    description = "Ejecuta las pruebas de integración entre servicios (@Tag(\"integration\"))."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.named("test"))
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
