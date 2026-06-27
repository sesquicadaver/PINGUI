plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "io.pingui"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val junitVersion = "5.11.4"

dependencies {
    implementation("org.yaml:snakeyaml:2.3")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("io.pingui.PinguiApplication")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.pingui.PinguiApplication"
    }
}
