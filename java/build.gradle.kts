plugins {
    java
    application
    jacoco
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
val appVersion = "0.1.0"

dependencies {
    implementation("org.yaml:snakeyaml:2.3")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")

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
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "INSTRUCTION"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "io/pingui/PinguiApplication.class",
                        "io/pingui/probe/ProcessRouteProbe.class",
                        "io/pingui/probe/icmp/LinuxJnaIcmpTransport*.class",
                        "io/pingui/probe/icmp/LinuxCLibrary*.class",
                        "io/pingui/probe/icmp/RawIcmpPermission.class",
                        "io/pingui/ui/MainController*.class",
                        "io/pingui/ui/GraphCanvas*.class",
                        "io/pingui/ui/HostItem*.class",
                    )
                }
            },
        ),
    )
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
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

tasks.register("jpackage") {
    group = "distribution"
    description = "Build platform-native installer (Linux .deb when available)"
    dependsOn("jpackageDeb")
}
