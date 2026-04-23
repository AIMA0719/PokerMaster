import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.library")
            apply("org.jetbrains.kotlin.android")
        }

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            // 라이브러리에는 targetSdk 가 deprecated 되었으므로 lint 만 정렬
            lint.targetSdk = libs.intVersion("targetSdk")
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
    }
}
