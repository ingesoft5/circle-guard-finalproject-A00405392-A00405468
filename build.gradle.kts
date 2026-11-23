plugins {
    id("org.springframework.boot") version "3.2.4" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
    kotlin("plugin.jpa") version "1.9.24" apply false
}

allprojects {
    group = "com.circleguard"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        // HU-20 (#27): /actuator/prometheus en todos los servicios (versión del BOM de Spring Boot).
        "runtimeOnly"("io.micrometer:micrometer-registry-prometheus")
        // HU-22 (#29): trazas con Micrometer Tracing + OpenTelemetry (exporta por OTLP a Jaeger) y logs en JSON
        // con trace_id. Versiones del BOM de Spring Boot, salvo el encoder de logstash.
        "implementation"("io.micrometer:micrometer-tracing-bridge-otel")
        "implementation"("io.opentelemetry:opentelemetry-exporter-otlp")
        "implementation"("net.logstash.logback:logstash-logback-encoder:7.4")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("com.h2database:h2")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // HU-22: el mismo logback-spring.xml (JSON con trace_id) en todos los servicios.
    extensions.configure<SourceSetContainer> {
        named("main") { resources.srcDir(rootProject.file("config/logging")) }
    }

    // Cobertura con JaCoCo: cada `test` deja el reporte XML que leen Jenkins y SonarQube.
    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.13"
    }
    tasks.named<Test>("test") {
        finalizedBy("jacocoTestReport")
    }
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
