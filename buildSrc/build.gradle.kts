plugins {
    `kotlin-dsl`
}

repositories {
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
    jcenter()
}

dependencies {
    implementation("org.beryx:badass-jlink-plugin:2.22.3")
}