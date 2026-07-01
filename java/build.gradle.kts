plugins {
    java
    application
    checkstyle
    jacoco
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.diffplug.spotless") version "6.25.0"
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
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
                        "io/pingui/AppInfo.class",
                        "io/pingui/CliProfileOverrides.class",
                        "io/pingui/probe/ProcessRouteProbe.class",
                        "io/pingui/probe/ProcessExpertPing.class",
                        "io/pingui/probe/ProcessHostPing.class",
                        "io/pingui/probe/PingExpertValidator.class",
                        "io/pingui/probe/RawIcmpRouteProbe.class",
                        "io/pingui/probe/PingOptionCatalog.class",
                        "io/pingui/probe/TraceCommandBuilder.class",
                        "io/pingui/probe/TraceCommandFactory.class",
                        "io/pingui/probe/TraceProcessTiming.class",
                        "io/pingui/probe/TracerouteExecutables.class",
                        "io/pingui/probe/TracerouteFlavorDetector.class",
                        "io/pingui/probe/LinuxTracerouteCommand.class",
                        "io/pingui/probe/MacTracerouteCommand.class",
                        "io/pingui/probe/WindowsTracertCommand.class",
                        "io/pingui/probe/UnixTraceOutputParser.class",
                        "io/pingui/probe/WindowsTraceOutputParser.class",
                        "io/pingui/probe/icmp/LinuxJnaIcmpTransport*.class",
                        "io/pingui/probe/icmp/LinuxCLibrary*.class",
                        "io/pingui/probe/icmp/RawIcmpPermission.class",
                        "io/pingui/ui/MainController*.class",
                        "io/pingui/ui/PingExpertDialog*.class",
                        "io/pingui/ui/GraphCanvas*.class",
                        "io/pingui/ui/HostItem*.class",
                        "io/pingui/ui/HostListCell*.class",
                        "io/pingui/ui/ProfileUiCoordinator*.class",
                        "io/pingui/ui/HostListPresenter*.class",
                        "io/pingui/ui/MonitorLifecycle*.class",
                        "io/pingui/ui/ViewModeController*.class",
                        "io/pingui/ui/RouteGraphPresenter*.class",
                        "io/pingui/ui/AppMenuDialogs*.class",
                    )
                }
            },
        ),
    )
}

javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("io.pingui.PinguiApplication")
}

checkstyle {
    toolVersion = "10.21.4"
    configFile = file("config/checkstyle/checkstyle.xml")
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}

spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        palantirJavaFormat("2.50.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "settings.gradle.kts")
        ktlint()
    }
}

tasks.check {
    dependsOn(tasks.named("spotlessCheck"))
    dependsOn(tasks.named("layerCheck"))
    dependsOn(tasks.named("checkstyleMain"))
    dependsOn(tasks.named("checkstyleTest"))
    dependsOn(tasks.jacocoTestCoverageVerification)
}

val generatedResources = layout.buildDirectory.dir("generated/resources")

tasks.register("generateBuildProperties") {
    group = "build"
    description = "Write pingui/build.properties with version, git sha, and CI build number"
    outputs.dir(generatedResources)
    doLast {
        val gitSha =
            runCatching {
                providers
                    .exec {
                        commandLine("git", "rev-parse", "--short", "HEAD")
                        isIgnoreExitValue = true
                    }
                    .standardOutput
                    .asText
                    .get()
                    .trim()
            }
                .getOrElse { "" }
                .ifBlank { "unknown" }
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.takeIf { it.isNotBlank() } ?: "local"
        val outDir = generatedResources.get().asFile.resolve("pingui")
        outDir.mkdirs()
        outDir
            .resolve("build.properties")
            .writeText(
                """
                version=$version
                gitSha=$gitSha
                buildNumber=$buildNumber
                """
                    .trimIndent(),
            )
    }
}

sourceSets.main {
    resources.srcDir(generatedResources)
}

tasks.named("processResources") {
    dependsOn("generateBuildProperties")
}

tasks.register<Exec>("layerCheck") {
    group = "verification"
    description = "Fail if lower layers import io.pingui.ui (B-063)"
    workingDir = projectDir
    commandLine("bash", "scripts/check-layer-deps.sh")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.pingui.PinguiApplication",
            "Implementation-Version" to version,
        )
    }
}

tasks.register<Exec>("jpackageDeb") {
    group = "distribution"
    description = "Build Linux .deb installer via jpackage (requires installDist)"
    dependsOn("installDist")
    val libDir = layout.buildDirectory.dir("install/pingui-java/lib")
    val distDir = layout.buildDirectory.dir("dist")
    val mainJar = "pingui-java-$version.jar"
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
    val mainJar = "pingui-java-$version.jar"
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
    val mainJar = "pingui-java-$version.jar"
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
