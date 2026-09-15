
// Glavni build fajl, zajednicka podesavanja za sve module
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.symbol.processing) apply false
    alias(libs.plugins.hilt.android) apply false
}