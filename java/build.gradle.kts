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

val appVersion = "0.1.0"

dependencies {
    implementation("org.yaml:snakeyaml:2.3")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
}

javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("io.pingui.PinguiApplication")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.pingui.PinguiApplication"
    }
}

tasks.register<Exec>("jpackageDeb") {
    group = "distribution"
    description = "Build Linux .deb installer via jpackage (requires installDist)"
    dependsOn("installDist")
    val libDir = layout.buildDirectory.dir("install/pingui-java/lib")
    val distDir = layout.buildDirectory.dir("dist")
    val mainJar = "pingui-java-${version}.jar"
    doFirst {
        distDir.get().asFile.mkdirs()
    }
    commandLine(
        "jpackage",
        "--input", libDir.get().asFile.absolutePath,
        "--name", "pingui",
        "--main-class", "io.pingui.PinguiApplication",
        "--main-jar", mainJar,
        "--type", "deb",
        "--java-options", "-Dprism.order=sw",
        "--vendor", "PINGUI",
        "--app-version", appVersion,
        "--dest", distDir.get().asFile.absolutePath,
    )
    onlyIf {
        System.getProperty("os.name", "").lowercase().contains("linux")
    }
}

tasks.register<Exec>("jpackageMsi") {
    group = "distribution"
    description = "Build Windows .msi installer via jpackage (requires installDist)"
    dependsOn("installDist")
    val libDir = layout.buildDirectory.dir("install/pingui-java/lib")
    val distDir = layout.buildDirectory.dir("dist")
    val mainJar = "pingui-java-${version}.jar"
    doFirst {
        distDir.get().asFile.mkdirs()
    }
    commandLine(
        "jpackage",
        "--input", libDir.get().asFile.absolutePath,
        "--name", "pingui",
        "--main-class", "io.pingui.PinguiApplication",
        "--main-jar", mainJar,
        "--type", "msi",
        "--java-options", "-Dprism.order=sw",
        "--vendor", "PINGUI",
        "--app-version", appVersion,
        "--dest", distDir.get().asFile.absolutePath,
    )
    onlyIf {
        System.getProperty("os.name", "").lowercase().contains("win")
    }
}

tasks.register<Exec>("jpackageDmg") {
    group = "distribution"
    description = "Build macOS .dmg installer via jpackage (requires installDist)"
    dependsOn("installDist")
    val libDir = layout.buildDirectory.dir("install/pingui-java/lib")
    val distDir = layout.buildDirectory.dir("dist")
    val mainJar = "pingui-java-${version}.jar"
    doFirst {
        distDir.get().asFile.mkdirs()
    }
    commandLine(
        "jpackage",
        "--input", libDir.get().asFile.absolutePath,
        "--name", "pingui",
        "--main-class", "io.pingui.PinguiApplication",
        "--main-jar", mainJar,
        "--type", "dmg",
        "--java-options", "-Dprism.order=sw",
        "--vendor", "PINGUI",
        "--app-version", appVersion,
        "--dest", distDir.get().asFile.absolutePath,
    )
    onlyIf {
        System.getProperty("os.name", "").lowercase().contains("mac")
    }
}

tasks.register("jpackage") {
    group = "distribution"
    description = "Build platform-native installer (.deb / .msi / .dmg when available)"
    dependsOn("jpackageDeb", "jpackageMsi", "jpackageDmg")
}

// Unit/integration tests — гілка beta (java/src/test).
