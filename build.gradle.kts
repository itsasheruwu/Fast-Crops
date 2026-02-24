plugins {
    java
}

group = "io.github.ash"
version = "1.1.0"

val java21 = JavaLanguageVersion.of(21)

java {
    toolchain {
        languageVersion.set(java21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.12.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(java21)
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.withType<JavaCompile>().configureEach {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion.set(java21)
    }
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(java21)
    }
}

tasks.register("verifyJava21Runtime") {
    doFirst {
        if (JavaVersion.current() != JavaVersion.VERSION_21) {
            throw GradleException(
                "This project must be built with Java 21. Current runtime: ${JavaVersion.current()}."
            )
        }
    }
}

tasks.named("check") {
    dependsOn("verifyJava21Runtime")
}

tasks.named("build") {
    dependsOn("verifyJava21Runtime")
}
