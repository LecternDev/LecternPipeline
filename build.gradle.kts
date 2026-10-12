plugins {
    id("java")
    application
}

group = "tlm.dev.lectern"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "tlm.dev.lectern.pipeline.PipelineMain"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jackson.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.test {
    useJUnitPlatform()
}
