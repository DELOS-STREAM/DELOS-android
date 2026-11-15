package com.limelight.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release build runs R8 (C-09). R8 cannot see reflection, so a class Gson reads or native code looks up by name needs
 * a keep rule, or the release build silently loses fields that the debug build has. This test finds those classes in the
 * sources and requires a rule for each; it also pins the release settings themselves.
 */
class ReleaseKeepRulesTest {
    private val app = File(".")
    private val rules = File(app, "proguard-rules.pro").readText()
    private val sources = File(app, "src/main/java").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()

    /** True when a `-keep class` rule names [simpleName] as a class or nested class, or keeps its whole package ([packageName].*). */
    private fun kept(simpleName: String, packageName: String? = null): Boolean =
        Regex("""-keep class [\w.$]*[.$]${Regex.escape(simpleName)} \{\*;}""").containsMatchIn(rules) ||
            (packageName != null && "-keep class $packageName.* {*;}" in rules)

    @Test
    fun everyClassGsonReadsHasAKeepRule() {
        val patterns = listOf(
            Regex("""fromJson\([^;\n]*?,\s*(\w+)(?:::class\.java|\.class)\)"""),
            Regex("""TypeToken<(\w+)>"""),
        )
        val models = sources.flatMap { file -> patterns.flatMap { p -> p.findAll(file.readText()).map { it.groupValues[1] } } }.toSet()
        // The search itself must keep finding them: an empty set would pass everything
        assertEquals(setOf("CachedMetadata", "Match", "ShortcutFile", "ProfilesData"), models)
        for (model in models) assertTrue("no -keep rule for the Gson class $model", kept(model))
    }

    @Test
    fun everyClassNativeCodeLooksUpHasAKeepRule() {
        val jni = File(app, "src/main/jni")
        val own = listOf(File(jni, "moonlight-core"), File(jni, "pyrowave-renderer")).flatMap { dir ->
            dir.walkTopDown().onEnter { it.name != "moonlight-common-c" && it.name != "openssl" }
                .filter { it.isFile && it.extension in setOf("c", "cpp", "h") }.toList()
        }
        val classes = own.flatMap { Regex("""FindClass\([^"]*"(com/limelight/[\w/$]+)"""").findAll(it.readText()).map { m -> m.groupValues[1] } }.toSet()
        // The search itself must keep finding them: an empty set would pass everything
        assertTrue("the native glue looks up LatencyThreads", "com/limelight/binding/video/LatencyThreads" in classes)
        // MoonBridge passes its own class to native init, which resolves the upcall methods by name (callbacks.c)
        assertTrue("callbacks.c resolves MoonBridge upcalls by name", "GetStaticMethodID(env, clazz, methods[i].name" in File(jni, "moonlight-core/callbacks.c").readText())
        assertTrue("no -keep rule for MoonBridge", kept("MoonBridge", "com.limelight.nvstream.jni"))
        for (name in classes) {
            val dotted = name.replace('/', '.')
            assertTrue("no -keep rule for the class native code looks up: $dotted", kept(dotted.substringAfterLast('.'), dotted.substringBeforeLast('.')))
        }
    }

    @Test
    fun theReleaseBuildShrinksCodeAndResourcesAndCarriesNoSigningConfig() {
        val gradle = File(app, "build.gradle").readText()
        val release = gradle.substringAfter("release {").substringBefore("}")
        assertTrue("minifyEnabled true" in release)
        assertTrue("shrinkResources true" in release)
        assertTrue("proguard-rules.pro" in release)
        // Signing happens outside the repository: no key material or signing setup in the build file
        assertFalse(Regex("""signingConfigs?\s*[.{=]""").containsMatchIn(gradle))
        assertFalse("storePassword" in gradle || "keyPassword" in gradle)
    }

    @Test
    fun noRuleIsLeftForCodeThatIsGone() {
        for (line in rules.lines().filter { it.startsWith("-keep class com.limelight.") }) {
            val name = line.removePrefix("-keep class ").substringBefore(' ')
            val path = name.substringBefore('$').removeSuffix(".*").replace('.', '/')
            val exists = listOf("$path.java", "$path.kt", path).any { File(app, "src/main/java/$it").exists() } ||
                // A Kotlin class declared in a file of another name
                sources.any { it.path.startsWith(File(app, "src/main/java/${path.substringBeforeLast('/')}").path) && Regex("""class ${name.substringAfterLast('.').substringBefore('$')}\b""").containsMatchIn(it.readText()) }
            assertTrue("keep rule for a class that does not exist: $line", exists)
        }
    }
}
