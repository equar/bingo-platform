allprojects {
    repositories {
        google()
        mavenCentral()
    }

    // Flutter integration_test requests Espresso 3.2+, whose legacy manifests
    // share a namespace. AGP 9 requires unique library namespaces.
    configurations.configureEach {
        resolutionStrategy.force(
            "androidx.test.espresso:espresso-core:3.7.0",
            "androidx.test.espresso:espresso-idling-resource:3.7.0",
        )
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
