plugins {
    id("application")
    id("org.javamodularity.moduleplugin") version "1.7.0"
    id("com.jcs.extras.extras-plugin")
}

group = "com.jcs"
version = "1.5.0-SNAPSHOT"

tasks.test {
    useJUnitPlatform()
}

application {
    mainModule.set("overlay.main")
    mainClass.set("com.jcs.overlay.App")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(15)
}

// Modularity stuff
tasks.withType<JavaExec> {
    modularity.inferModulePath.set(true)
}
modularity.patchModule("slf4j.log4j12", "log4j-1.2.17.jar")
modularity.disableEffectiveArgumentsAdjustment()

// JLink plugin
jlink {
    addOptions("--strip-debug", "--compress=2", "--no-header-files", "--no-man-pages")
    forceMerge("slf4j-api")
    mergedModule {
        additive = true
        requires("jdk.crypto.ec") // Fixes random SSLHandshakeException...
    }
    launcher {
        windowsScriptTemplate = file("jlink/windows_launcher_template.txt")
    }
}

repositories {
    flatDir {
        dirs("libs")
    }
    jcenter()
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
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
    implementation("com.merakianalytics.orianna", "orianna", "4.0.0-SNAPSHOT")

    // Moshi
    implementation("com.squareup.moshi", "moshi", "1.11.0")
    implementation(":moshi-adapters-1.11.1-SNAPSHOT")

    // CEF (from JAR)
    implementation(":jogl-all")
    implementation(":gluegen-rt")
    implementation(":jcef-win64")
    implementation(":gluegen-rt-natives-windows-amd64")
    implementation(":jogl-all-natives-windows-amd64")

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
