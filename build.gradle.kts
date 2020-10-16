import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("application")
    id("java")
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

group = "com.jcs"
version = "1.4.2-SNAPSHOT"

configurations {
    create("implementationW32")
    create("implementationW64")
    create("W32")
    create("W64")

    compileOnly {
        extendsFrom(configurations["implementationW32"])
        extendsFrom(configurations["implementationW64"])
    }
    "W32" {
        extendsFrom(configurations["implementationW32"])
        extendsFrom(configurations["runtimeClasspath"])
    }
    "W64" {
        extendsFrom(configurations["implementationW64"])
        extendsFrom(configurations["runtimeClasspath"])
    }
}

tasks.run.get().enabled = false

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClassName = "com.jcs.overlay.App"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    flatDir {
        dirs("libs")
    }
    jcenter()
    mavenCentral()
    google()
}

dependencies {
    implementation("org.java-websocket", "Java-WebSocket", "1.4.0")
    implementation("net.java.dev.jna", "jna-platform", "5.3.1")
    implementation("org.apache.commons", "commons-lang3", "3.9")
//    implementation("com.typesafe", "config", "1.4.0")
    implementation(":config")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.9.0")

    // Orianna
    implementation("com.merakianalytics.orianna", "orianna", "4.0.0-rc7")

    // Moshi
    implementation("com.squareup.moshi", "moshi", "1.8.0")
    implementation("com.squareup.moshi", "moshi-adapters", "1.8.0")

    // CEF (from JAR)
    implementation(":jogl-all")
    implementation(":gluegen-rt")
    "implementationW32"(":jcef-win32")
    "implementationW32"(":gluegen-rt-natives-windows-i586")
    "implementationW32"(":jogl-all-natives-windows-i586")
    "implementationW64"(":jcef-win64")
    "implementationW64"(":gluegen-rt-natives-windows-amd64")
    "implementationW64"(":jogl-all-natives-windows-amd64")

    // SLF4J
    implementation("org.slf4j", "slf4j-api", "1.8.0-beta4")
    implementation("org.slf4j", "slf4j-log4j12", "1.8.0-beta4")

    // Other
    compileOnly("org.jetbrains", "annotations", "+")
    compileOnly("com.google.code.findbugs", "jsr305", "3.0.2")
    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.4.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4.2")
}

tasks.register<ShadowJar>("shadowJarW32") {
    group = "shadow"
    description = "Create a combined JAR of project and runtime dependencies for the win32 platform"

    val regex = Regex("""(?:\d+\.)+(?:\d+)?(?:pre|[a-z]?)?""")
    archiveClassifier.set("win32")
    archiveVersion.set("v" + (regex.find(project.version as CharSequence)?.value))

    manifest.attributes["Main-Class"] = project.application.mainClassName

    configurations = listOf(project.configurations["W32"])
    from(project.sourceSets.main.get().output)
}

tasks.register<ShadowJar>("shadowJarW64") {
    group = "shadow"
    description = "Create a combined JAR of project and runtime dependencies for the win64 platform"

    val regex = Regex("""(?:\d+\.)+(?:\d+)?(?:pre|[a-z]?)?""")
    archiveClassifier.set("win64")
    archiveVersion.set("v" + (regex.find(project.version as CharSequence)?.value))

    manifest.attributes["Main-Class"] = project.application.mainClassName

    configurations = listOf(project.configurations["W64"])
    from(project.sourceSets.main.get().output)
}

tasks.register<JavaExec>("runW32") {
    classpath = project.sourceSets.main.get().output + configurations["W32"]
    main = application.mainClassName
}
tasks.register<JavaExec>("runW64") {
    classpath = project.sourceSets.main.get().output + configurations["W64"]
    main = application.mainClassName
}