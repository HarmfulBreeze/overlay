package com.jcs.extras

import com.jcs.extras.Extras_plugin_gradle.Constants.Companion.UNZIP_CEF_TASK_NAME
import com.jcs.extras.Extras_plugin_gradle.Constants.Companion.UNZIP_REQUIREMENTS_TASK_NAME
import com.jcs.extras.Extras_plugin_gradle.Constants.Companion.UNZIP_WEBZIP_TASK_NAME
import org.beryx.jlink.JlinkPlugin

class Constants {
    companion object {
        const val UNZIP_REQUIREMENTS_TASK_NAME = "unzipRequirements"
        const val UNZIP_CEF_TASK_NAME = "unzipCef"
        const val UNZIP_WEBZIP_TASK_NAME = "unzipWebzip"
    }
}

plugins {
    id("org.beryx.jlink")
}

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

tasks.withType<JavaExec> {
    dependsOn(UNZIP_REQUIREMENTS_TASK_NAME)
}

tasks.register(UNZIP_REQUIREMENTS_TASK_NAME) {
    finalizedBy(UNZIP_CEF_TASK_NAME, UNZIP_WEBZIP_TASK_NAME)
}

tasks.register<Copy>(UNZIP_CEF_TASK_NAME) {
    outputs.upToDateWhen { file("libs/lib/win64").exists() }
    from(project.zipTree("libs/lib/win64.zip"))
    into("libs/lib")
}

tasks.register<Copy>(UNZIP_WEBZIP_TASK_NAME) {
    outputs.upToDateWhen { file("web/index.html").exists() }
    from(project.zipTree("web/web.zip"))
    into("web")
}

tasks.register<Zip>("overlayDistZip") {
    archiveWork(this)
}

tasks.register<Tar>("overlayDistTar") {
    archiveWork(this)
}

fun archiveWork(archiveTask: AbstractArchiveTask) {
    archiveTask.group = "distribution"
    archiveTask.dependsOn(JlinkPlugin.getTASK_NAME_JLINK(), UNZIP_REQUIREMENTS_TASK_NAME)
    val imageDir = jlink.imageDir.asFile.get()
    val zipNameNoExt = archiveTask.archiveFile.get().asFile.nameWithoutExtension
    val launcherFileName = jlink.launcherData.get().name
    archiveTask.destinationDirectory.set(file("${buildDir}/distribution"))
    archiveTask.duplicatesStrategy = DuplicatesStrategy.FAIL
    archiveTask.from(imageDir) {
        include("**")
        exclude("bin/**")
    }
    archiveTask.from(imageDir) {
        include("bin/**")
        exclude("bin/$launcherFileName")
        fileMode = 755
    }
    archiveTask.from("$imageDir/bin") { // moves launcher to root
        include("$launcherFileName.bat")
    }
    archiveTask.from("${rootDir}/web") {
        include("**")
        exclude("web.zip",
                "img/custom/logos/*",
                "img/icon",
                "img/splash",
                "img/tile")
        into("web")
    }
    archiveTask.from("${rootDir}/libs/lib/win64") {
        into("lib/cef")
        fileMode = 755
    }
    archiveTask.into(zipNameNoExt)
}