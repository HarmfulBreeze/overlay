import org.javamodularity.moduleplugin.tasks.ModularJavaExec

plugins {
    id("application")
    id("java")
    id("org.javamodularity.moduleplugin") version "1.7.0"
}

group = "com.jcs"
version = "1.5.0-SNAPSHOT"

configurations {
    create("implementationW32")
    create("implementationW64")

    compileOnly {
        extendsFrom(configurations["implementationW32"])
        extendsFrom(configurations["implementationW64"])
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(15)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainModule.set("overlay.main")
    mainClass.set("com.jcs.overlay.App")
}

java {
    modularity.inferModulePath.set(true)
}

tasks.withType<ModularJavaExec> {
    modularity.inferModulePath.set(true)
}

modularity.patchModule("slf4j.log4j12", "log4j-1.2.17.jar")
modularity.disableEffectiveArgumentsAdjustment()

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
//    implementation("com.merakianalytics.orianna", "orianna", "4.0.0-rc7")
    implementation(":orianna-4.0.0-SNAPSHOT")
    implementation(fileTree("libs/orianna") { include("*.jar") })

    // Moshi
    implementation("com.squareup.moshi", "moshi", "1.11.0")
//    implementation("com.squareup.moshi", "moshi-adapters", "1.11.0")
    implementation(":adapters-1.11.1-SNAPSHOT")

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
    implementation("uk.org.lidalia", "sysout-over-slf4j", "1.0.2")

    // Other
    implementation("org.jetbrains", "annotations", "+")
    compileOnly("com.google.code.findbugs", "jsr305", "3.0.2")
    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.4.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4.2")
}

tasks.register<ModularJavaExec>("runW32") {
    group = "application"
    mainModule.set("overlay.main")
    mainClass.set("com.jcs.overlay.App")
    configurations["implementation"].extendsFrom(configurations["implementationW32"])
}

tasks.register<ModularJavaExec>("runW64") {
    group = "application"
    mainModule.set("overlay.main")
    mainClass.set("com.jcs.overlay.App")
    configurations["implementation"].extendsFrom(configurations["implementationW64"])
}