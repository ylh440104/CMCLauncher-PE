plugins {
    java
}

val lwjglVersion = "3.4.1"
group = "org.lwjgl.glfw"

configurations {
    create("lwjglModules") {
        isCanBeResolved = true
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "../compileOnly", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "libs/$lwjglVersion", "include" to listOf("*.jar"))))
    add(
        "lwjglModules",
        fileTree(mapOf("dir" to "libs/$lwjglVersion", "include" to listOf("*.jar")))
    )
    add("lwjglModules", project(":LWJGL:patches"))
    implementation(project(":LWJGL:patches"))
    implementation(libs.jspecify)

}

tasks.jar {

    inputs.files(configurations["lwjglModules"])

    val excludedModules = arrayOf(
        "lwjgl-lwjglx.jar",
        "lwjgl.jar",
        "jsr305.jar",
        "lwjgl-freetype.jar",
        "lwjgl-jemalloc.jar",
        "lwjgl-nanovg.jar",
        "lwjgl-openal.jar",
        "lwjgl-stb.jar",
        "lwjgl-tinyfd.jar",
        "lwjgl-shaderc.jar",
        "lwjgl-spvc.jar",
        "lwjgl-vma.jar",
        "lwjgl-vulkan.jar",
        "lwjgl-spng.jar"
    )

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("lwjgl-${lwjglVersion}-merged-modules")
    destinationDirectory.set(file("$rootDir/CMCLauncher/src/main/assets/app_runtime/lwjgl/${lwjglVersion}"))

    from({

        val includedModules = configurations["lwjglModules"].filter { dep ->
            !excludedModules.any { it == dep.name }
        }
        val coreJar = includedModules.find { it.name == "lwjgl.jar" }
        val jarList =
            if (coreJar != null) listOf(coreJar) + (includedModules - coreJar) else includedModules
        println("Merging LWJGL $lwjglVersion modules in the order: ")
        jarList.map {
            println(it.name)
            if (it.isDirectory) it else zipTree(it)
        }
    })

    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    doLast {
        val excludedModulesFileList = excludedModules.flatMap { fileName ->
            configurations["lwjglModules"].filter { it.name == fileName }
        }
        copy {

            from(excludedModulesFileList)
            into(archiveFile.get().asFile.parentFile)
        }
    }
    outputs.files(excludedModules.map { path -> File(destinationDirectory.get().asFile, path) })
    exclude("net/java/openjdk/cacio/ctc/**")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
