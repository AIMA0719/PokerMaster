import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias).orElseThrow { IllegalStateException("Missing version: $alias") }
        .requiredVersion.toInt()

internal fun VersionCatalog.strVersion(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("Missing version: $alias") }
        .requiredVersion
