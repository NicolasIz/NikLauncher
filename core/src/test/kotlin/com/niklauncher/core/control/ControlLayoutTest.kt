package com.niklauncher.core.control

import com.niklauncher.core.input.Glfw
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlLayoutTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `the default layout keeps the centre of the screen clear`() {
        val layout = ControlPresets.default()
        val centreOccupants = (layout.buttons.map { it.x to it.y } + layout.joysticks.map { it.x to it.y })
            .filter { (x, y) -> x in 0.35f..0.65f && y in 0.2f..0.8f }

        assertTrue(
            centreOccupants.isEmpty(),
            "the look and attack area must stay free, but found $centreOccupants",
        )
    }

    @Test
    fun `the default layout covers the controls a session needs`() {
        val layout = ControlPresets.default()
        val keys = layout.buttons.mapNotNull { it.action.asBinding() }

        assertTrue(keys.contains(com.niklauncher.core.input.ControlBinding.key(Glfw.KEY_SPACE)), "no jump")
        assertTrue(keys.contains(com.niklauncher.core.input.ControlBinding.key(Glfw.KEY_E)), "no inventory")
        assertTrue(
            keys.contains(com.niklauncher.core.input.ControlBinding.mouse(Glfw.MOUSE_BUTTON_RIGHT)),
            "no use/place button",
        )
        assertEquals(1, layout.joysticks.size, "expected a movement pad")
    }

    @Test
    fun `sneak latches instead of needing a held finger`() {
        val sneak = ControlPresets.default().buttons.single { it.id == "sneak" }
        assertTrue(sneak.toggle)
    }

    @Test
    fun `controls needed in menus stay visible there`() {
        val layout = ControlPresets.default()
        assertTrue(layout.buttons.single { it.id == "cursor" }.visibleInMenu)
        assertTrue(layout.buttons.single { it.id == "menu" }.visibleInMenu)
    }

    @Test
    fun `normalising pulls out-of-range controls back on screen`() {
        val broken = ControlLayout(
            id = "broken",
            name = "Broken",
            buttons = listOf(
                ControlButton(
                    id = "a",
                    label = "A",
                    x = 5f,
                    y = -2f,
                    widthDp = 1000f,
                    opacity = 4f,
                    cornerPercent = 90,
                    action = ControlAction.none,
                ),
            ),
        )

        val button = broken.normalised().buttons.single()

        assertEquals(1f, button.x)
        assertEquals(0f, button.y)
        assertEquals(ControlButton.MAX_SIZE_DP, button.widthDp)
        assertEquals(1f, button.opacity)
        assertEquals(50, button.cornerPercent)
    }

    @Test
    fun `normalising drops duplicate ids`() {
        val layout = ControlLayout(
            id = "dupes",
            name = "Dupes",
            buttons = listOf(
                ControlButton(id = "a", label = "first", x = 0.1f, y = 0.1f, action = ControlAction.none),
                ControlButton(id = "a", label = "second", x = 0.2f, y = 0.2f, action = ControlAction.none),
            ),
        )

        val buttons = layout.normalised().buttons
        assertEquals(1, buttons.size)
        assertEquals("first", buttons.single().label)
    }

    @Test
    fun `adding a button with an existing id replaces it`() {
        val layout = ControlPresets.default()
        val moved = layout.buttons.single { it.id == "jump" }.copy(x = 0.5f)

        val updated = layout.withButton(moved)

        assertEquals(layout.buttons.size, updated.buttons.size)
        assertEquals(0.5f, updated.buttons.single { it.id == "jump" }.x)
    }

    @Test
    fun `removing an element takes buttons and joysticks alike`() {
        val layout = ControlPresets.default()
        assertNull(layout.withoutElement("jump").buttons.firstOrNull { it.id == "jump" })
        assertTrue(layout.withoutElement("move").joysticks.isEmpty())
    }

    @Test
    fun `a layout survives a round trip through json`() {
        val original = ControlPresets.default()
        val decoded = json.decodeFromString(ControlLayout.serializer(), json.encodeToString(ControlLayout.serializer(), original))

        assertEquals(original, decoded)
    }

    @Test
    fun `an older saved layout still parses`() {
        // User layouts must survive app updates; unknown fields cannot be fatal.
        val stored = """
            {
              "id": "mine", "name": "Mío", "schemaVersion": 1,
              "buttons": [
                { "id": "jump", "label": "Saltar", "x": 0.9, "y": 0.7,
                  "action": { "kind": "KEY", "code": 32 }, "somethingNew": true }
              ]
            }
        """.trimIndent()

        val layout = json.decodeFromString(ControlLayout.serializer(), stored)

        assertEquals("mine", layout.id)
        val jump = assertNotNull(layout.buttons.singleOrNull())
        assertEquals(Glfw.KEY_SPACE, jump.action.code)
    }

    @Test
    fun `presets all normalise to themselves`() {
        for (preset in ControlPresets.all()) {
            assertEquals(preset, preset.normalised(), "preset ${preset.id} is not already valid")
        }
    }

    @Test
    fun `scroll actions emit only on press`() {
        val scroll = ControlAction(ControlActionKind.SCROLL, 1)
        assertNotNull(scroll.toEvent(Glfw.PRESS))
        assertNull(scroll.toEvent(Glfw.RELEASE), "a scroll must not fire again on release")
    }
}
