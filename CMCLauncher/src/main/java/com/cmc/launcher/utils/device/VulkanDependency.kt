package com.cmc.launcher.utils.device

import com.cmc.launcher.game.version.installed.utils.isBiggerVer
import com.cmc.launcher.game.version.installed.utils.isLowerVer
import org.jackhuang.hmcl.util.versioning.GameVersionNumber

private val NUMBER_SUFFIX = Regex("^(.*?)(\\d+)$")

private fun String.nextNumberVersion(): String? {
    val match = NUMBER_SUFFIX.find(this) ?: return null
    val number = match.groupValues[2].toIntOrNull() ?: return null
    return match.groupValues[1] + (number + 1)
}

private fun String.prevNumberVersion(): String? {
    val match = NUMBER_SUFFIX.find(this) ?: return null
    val number = match.groupValues[2].toIntOrNull() ?: return null
    if (number <= 0) return null
    return match.groupValues[1] + (number - 1)
}

private val PRERELEASE_SUFFIX = Regex("^(.+)-(?:snapshot|pre|rc)-\\d+$")

fun normalizeMcVersion(mcVersion: String): String {
    return PRERELEASE_SUFFIX.find(mcVersion)?.groupValues?.get(1) ?: mcVersion
}

enum class VulkanDependencyLevel {

    REQUIRED,

    OPTIONAL,

    UNUSED
}

sealed interface McVersionSpan {

    val since: String

    val displayText: String

    val exclusiveEnd: String?

    operator fun contains(mcVersion: String): Boolean
}

data class McSingleVersion(val version: String) : McVersionSpan {
    override val since: String
        get() = version
    override val displayText: String
        get() = version
    override val exclusiveEnd: String?
        get() = version.nextNumberVersion()

    override fun contains(mcVersion: String): Boolean {
        return normalizeMcVersion(mcVersion) == version
    }
}

data class McVersionRange(
    val from: String,
    val to: String
) : McVersionSpan {
    override val since: String
        get() = from
    override val displayText: String
        get() = "$from-$to"
    override val exclusiveEnd: String?
        get() = to.nextNumberVersion()

    override fun contains(mcVersion: String): Boolean {
        val version = normalizeMcVersion(mcVersion)
        return !version.isLowerVer(from) && !version.isBiggerVer(to)
    }
}

data class McVersionOnward(override val since: String) : McVersionSpan {
    override val displayText: String
        get() = "$since+"
    override val exclusiveEnd: String?
        get() = null

    override fun contains(mcVersion: String): Boolean {
        return !normalizeMcVersion(mcVersion).isLowerVer(since)
    }
}

data class VulkanDependency(
    val name: String,

    val requiredIn: List<McVersionSpan> = emptyList(),

    val optionalIn: List<McVersionSpan> = emptyList(),

    val unusedIn: List<McVersionSpan> = emptyList()
) {

    fun levelAt(mcVersion: String): VulkanDependencyLevel {
        return when {
            requiredIn.any { mcVersion in it } -> VulkanDependencyLevel.REQUIRED
            optionalIn.any { mcVersion in it } -> VulkanDependencyLevel.OPTIONAL
            else -> VulkanDependencyLevel.UNUSED
        }
    }
}

class VulkanDependencyBuilder(private val name: String) {
    private val requiredIn = mutableListOf<McVersionSpan>()
    private val optionalIn = mutableListOf<McVersionSpan>()
    private val unusedIn = mutableListOf<McVersionSpan>()

    fun requiredAt(version: String) {
        requiredIn += McSingleVersion(version)
    }

    fun requiredBetween(from: String, to: String) {
        requiredIn += McVersionRange(from, to)
    }

    fun requiredFrom(since: String) {
        requiredIn += McVersionOnward(since)
    }

    fun optionalAt(version: String) {
        optionalIn += McSingleVersion(version)
    }

    fun optionalBetween(from: String, to: String) {
        optionalIn += McVersionRange(from, to)
    }

    fun optionalFrom(since: String) {
        optionalIn += McVersionOnward(since)
    }

    fun unusedAt(version: String) {
        unusedIn += McSingleVersion(version)
    }

    fun unusedBetween(from: String, to: String) {
        unusedIn += McVersionRange(from, to)
    }

    fun unusedFrom(since: String) {
        unusedIn += McVersionOnward(since)
    }

    fun build(): VulkanDependency = VulkanDependency(name, requiredIn, optionalIn, unusedIn)
}

private fun vulkanDependency(name: String, block: VulkanDependencyBuilder.() -> Unit): VulkanDependency {
    return VulkanDependencyBuilder(name).apply(block).build()
}

object VulkanRequirements {

    const val VULKAN_REQUIREMENTS_VERSION = 1

    const val MIN_MC_VERSION = "26.2"

    val EXTENSIONS: List<VulkanDependency> = listOf(
        vulkanDependency("VK_KHR_dynamic_rendering") {
            requiredBetween(MIN_MC_VERSION, "26.3")
        },
        vulkanDependency("VK_KHR_push_descriptor") {
            requiredBetween(MIN_MC_VERSION, "26.3")
        },
        vulkanDependency("VK_KHR_synchronization2") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("VK_EXT_vertex_attribute_divisor") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("VK_KHR_swapchain") { requiredFrom(MIN_MC_VERSION) }
    )

    val FEATURES: List<VulkanDependency> = listOf(
        vulkanDependency("multiDrawIndirect") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("fillModeNonSolid") {
            requiredAt(MIN_MC_VERSION)
            optionalFrom("26.3")
        },
        vulkanDependency("drawIndirectFirstInstance") {
            unusedAt(MIN_MC_VERSION)
            requiredFrom("26.3")
        },
        vulkanDependency("samplerAnisotropy") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("shaderDrawParameters") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("timelineSemaphore") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("hostQueryReset") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("synchronization2") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("dynamicRendering") {
            requiredBetween(MIN_MC_VERSION, "26.3")
        },
        vulkanDependency("vertexAttributeInstanceRateDivisor") { requiredFrom(MIN_MC_VERSION) }
    )

    val PROFILE_VERSIONS: List<String> = (EXTENSIONS + FEATURES)
        .flatMap { it.requiredIn + it.optionalIn + it.unusedIn }
        .flatMap { listOfNotNull(it.since, it.exclusiveEnd) }
        .distinct()
        .sortedWith { a, b -> GameVersionNumber.compare(a, b) }
}

data class VulkanDependencyStatus(
    val dependency: VulkanDependency,
    val level: VulkanDependencyLevel,

    val supported: Boolean
)

data class VulkanSupport(
    val mcVersion: String,
    val extensionStatuses: List<VulkanDependencyStatus>,
    val featureStatuses: List<VulkanDependencyStatus>
) {
    private val allStatuses: List<VulkanDependencyStatus>
        get() = extensionStatuses + featureStatuses

    val missingRequired: List<VulkanDependencyStatus>
        get() = allStatuses.filter { it.level == VulkanDependencyLevel.REQUIRED && !it.supported }

    val missingOptional: List<VulkanDependencyStatus>
        get() = allStatuses.filter { it.level == VulkanDependencyLevel.OPTIONAL && !it.supported }

    val isSupported: Boolean
        get() = missingRequired.isEmpty()
}

data class VulkanProfileSupport(
    val since: String,

    val until: String?,
    val supported: Boolean
) {

    val versionRangeText: String
        get() = when (until) {
            null -> "$since+"
            since.nextNumberVersion() -> since
            else -> until.prevNumberVersion()?.let { "$since-$it" } ?: "$since~$until"
        }
}

fun VulkanCapabilities.supports(dependency: VulkanDependency): Boolean {
    val isExtension = VulkanRequirements.EXTENSIONS.any { it.name == dependency.name }
    return if (isExtension) {
        dependency.name in extensions
    } else {
        features[dependency.name] == true
    }
}

fun VulkanCapabilities.supportFor(mcVersion: String): VulkanSupport {
    fun statusOf(dependency: VulkanDependency): VulkanDependencyStatus {
        return VulkanDependencyStatus(dependency, dependency.levelAt(mcVersion), supports(dependency))
    }

    return VulkanSupport(
        mcVersion = mcVersion,
        extensionStatuses = VulkanRequirements.EXTENSIONS.map(::statusOf),
        featureStatuses = VulkanRequirements.FEATURES.map(::statusOf)
    )
}

fun VulkanCapabilities.profileSupport(): List<VulkanProfileSupport> {
    val versions = VulkanRequirements.PROFILE_VERSIONS
    if (!isVersionSupported) {
        return listOf(VulkanProfileSupport(since = versions.first(), until = null, supported = false))
    }
    return versions.mapIndexed { index, since ->
        VulkanProfileSupport(
            since = since,
            until = versions.getOrNull(index + 1),
            supported = supportFor(since).isSupported
        )
    }.fold(mutableListOf()) { merged, profile ->
        val last = merged.lastOrNull()
        if (last != null && last.supported == profile.supported) {
            merged[merged.lastIndex] = last.copy(until = profile.until)
        } else {
            merged.add(profile)
        }
        merged
    }
}
