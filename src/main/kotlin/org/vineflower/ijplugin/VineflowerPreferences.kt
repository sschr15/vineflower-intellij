package org.vineflower.ijplugin

import com.intellij.openapi.diagnostic.logger
import javax.swing.JComponent

abstract class VineflowerPreferences {
    open class SettingsEntry(
        val name: String,
        val component: JComponent,
        val description: String?,
        val group: String? = null,
    )

    enum class Type {
        BOOLEAN, INTEGER, STRING
    }

    abstract fun setupSettings(entries: MutableList<SettingsEntry>, settingsMap: MutableMap<String, String>)

    companion object {
        fun create(): VineflowerPreferences = try {
            NewVineflowerPreferences()
        } catch (e: Throwable) {
            logger<VineflowerPreferences>().warn("Failed to create new preferences, falling back to classic", e)
            ClassicVineflowerPreferences
        }
    }
}
