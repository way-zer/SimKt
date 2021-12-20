plugins {
    kotlin("jvm") version "1.6.10-RC"
    `maven-publish`
}

group = "cf.wayzer"
version = "1.0-SNAPSHOT"

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
    }
    create("examples") {
        java.setSrcDirs(listOf("examples"))
        compileClasspath += main.get().compileClasspath + main.get().output
        runtimeClasspath += main.get().runtimeClasspath + main.get().output
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-RC2")
    implementation("org.apache.commons:commons-rng-simple:1.4")
    implementation("org.apache.commons:commons-rng-sampling:1.4")
    val examplesImplementation by configurations
    examplesImplementation(kotlin("script-runtime"))
    examplesImplementation("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:3.1.1")
//    examplesImplementation("org.jetbrains.lets-plot:lets-plot-batik:2.2.1")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
}

publishing {
    publications {
        create("main", MavenPublication::class.java) {
            from(components["java"])
            artifact(tasks["kotlinSourcesJar"]) {
                classifier = "sources"
            }
        }
    }
}