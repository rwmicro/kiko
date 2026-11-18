package app.kiko

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/** Base des fuseaux horaires : sans elle, le navigateur ne connaît que UTC et le fuseau local. */
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule : JsAny

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    loadTimeZones(JsJodaTimeZoneModule)
    ComposeViewport(viewportContainerId = "composeApp") { App() }
}

@Suppress("UNUSED_PARAMETER")
private fun loadTimeZones(module: JsAny) = Unit
