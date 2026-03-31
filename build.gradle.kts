plugins {
    id("com.android.application") version "9.1.0" apply false
    // Kotlin 需 ≤2.3.10：GitHub CodeQL 尚未支援 2.3.20（見 codeql / Kotlin 支援矩陣）。
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
}
