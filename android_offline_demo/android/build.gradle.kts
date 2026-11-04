plugins {
    id("org.jetbrains.kotlin.android") apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// BEGIN CNN JVM TARGET FIX
// Only align the tflite_flutter Kotlin target with its Java 11 target.
subprojects {
    if (name == "tflite_flutter") {
        plugins.withId("com.android.library") {
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>()
                .configureEach {
                    compilerOptions.jvmTarget.set(
                        org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
                    )
                }
        }
    }
}
// END CNN JVM TARGET FIX
