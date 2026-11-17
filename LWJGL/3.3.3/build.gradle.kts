plugins {
    java
}

val lwjglVersion = "3.3.3"
group = "org.lwjgl.glfw"

configurations {
    create("lwjglModules") {
        isCanBeResolved = true
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "../compileOnly", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "libs/$lwjglVersion", "include" to listOf("*.jar"))))

    val lwjglModules = fileTree("libs/$lwjglVersion") {
        include("*.jar")
        exclude("jsr305.jar")
    }
    add("lwjglModules", lwjglModules)
    add("lwjglModules", project(":LWJGL:patches"))
    implementation(project(":LWJGL:patches"))
}

tasks.jar {

    inputs.files(configurations["lwjglModules"])

    val excludedModules = arrayOf(
        "lwjgl.jar",
        "lwjgl-freetype.jar",

        "lwjgl-lwjglx.jar",
        "lwjgl-jemalloc.jar",
        "lwjgl-nanovg.jar",
        "lwjgl-openal.jar",

        "lwjgl-sdl.jar",
        "lwjgl-shaderc.jar",
        "lwjgl-spng.jar",
        "lwjgl-spvc.jar",
        "lwjgl-stb.jar",
        "lwjgl-tinyfd.jar",
        "lwjgl-vma.jar",
        "lwjgl-vulkan.jar"
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
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
