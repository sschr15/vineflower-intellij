package org.vineflower.ijplugin

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import javax.swing.event.DocumentEvent

class NewVineflowerPreferences : VineflowerPreferences() {
    val ignoredPreferences = setOf(
        "banner",
        "bytecode-source-mapping",
        "new-line-separator",
        "log-level",
        "user-renamer-class",
        "thread-count",
        "max-time-per-method",
        "indent-string", // Custom implementation
    )

    val defaultOverrides = mapOf(
        "hide-default-constructor" to "0",
        "decompile-generics" to "1",
        "remove-synthetic" to "1",
        "remove-bridge" to "1",
        "new-line-separator" to "1",
        "banner" to "//\n// Source code recreated from a .class file by Vineflower\n//\n\n",
        "max-time-per-method" to "0",
        "ignore-invalid-bytecode" to "1",
        "verify-anonymous-classes" to "1",
        "indent-string" to " ".repeat(CodeStyle.getDefaultSettings().indentOptions.INDENT_SIZE),
        "__unit_test_mode__" to if (ApplicationManager.getApplication().isUnitTestMode) "1" else "0",
    )

    val nameOverrides = mapOf(
        "keep-literals" to "Literals As-Is",
        "decompiler-java4" to "Resugar 1-4 Class Refs",
    )

    data class Option(
        val key: String,
        val type: Type,
        val name: String,
        val description: String,
        val plugin: String?,
        val defaultValue: String?,
    ) {
        constructor(vfOption: Any, vfClass: Class<*>) : this(
            key = vfClass.getMethod("id").invoke(vfOption) as String,
            type = when (vfClass.getMethod("type").invoke(vfOption).toString()) {
                "bool" -> Type.BOOLEAN
                "int" -> Type.INTEGER
                else -> Type.STRING
            },
            name = vfClass.getMethod("name").invoke(vfOption) as String,
            description = vfClass.getMethod("description").invoke(vfOption) as String,
            plugin = vfClass.getMethod("plugin").invoke(vfOption) as String?,
            defaultValue = vfClass.getMethod("defaultValue").invoke(vfOption) as String?,
        )
    }

    private val classLoader = VineflowerState.getInstance().getVineflowerClassLoader().getNow(null)
    private val optionClass = classLoader.loadClass("org.jetbrains.java.decompiler.api.DecompilerOption")
    private val getAllOptions = optionClass.getMethod("getAll")
    private val initVineflower = classLoader.loadClass("org.jetbrains.java.decompiler.main.Init")
        .getMethod("init")

    override fun setupSettings(entries: MutableList<SettingsEntry>, settingsMap: MutableMap<String, String>) {
        initVineflower(null)

        val options = (getAllOptions(null) as List<*>)
            .map { Option(it!!, optionClass) }
            .filter { it.key !in ignoredPreferences }

        for (option in options) {
            if (option.key in ignoredPreferences) continue

            val defaultValue = if (option.key in defaultOverrides) defaultOverrides[option.key] else option.defaultValue
            val currentValue = settingsMap[option.key] ?: defaultValue

            val component = when (option.type) {
                Type.BOOLEAN -> JBCheckBox().apply { 
                    isSelected = currentValue == "1"
                    addActionListener {
                        val newValue = if (isSelected) "1" else "0"
                        if (newValue != defaultValue) {
                            settingsMap[option.key] = newValue
                        } else {
                            settingsMap.remove(option.key)
                        }
                    }
                }
                Type.STRING -> JBTextField(currentValue).apply {
                    columns = 20
                    document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            val newValue = text
                            if (newValue != defaultValue) {
                                settingsMap[option.key] = newValue
                            } else {
                                settingsMap.remove(option.key)
                            }
                        }
                    })
                }
                Type.INTEGER -> JBIntSpinner(currentValue?.toInt() ?: 0, 0, Int.MAX_VALUE).apply {
                    addChangeListener {
                        val newValue = value.toString()
                        if (newValue != defaultValue) {
                            settingsMap[option.key] = newValue
                        } else {
                            settingsMap.remove(option.key)
                        }
                    }
                }
            }

            val name = nameOverrides[option.key] ?: option.name
            val desc = option.description

            entries.add(SettingsEntry(name, component, desc, option.plugin))
        }

        run {
            val currentIndentString = settingsMap["indent-string"] ?: defaultOverrides["indent-string"]!!
            val component = JBIntSpinner(currentIndentString.length, 0, Int.MAX_VALUE).apply {
                addChangeListener {
                    val newValue = " ".repeat(value.toString().toInt())
                    if (newValue != defaultOverrides["indent-string"]) {
                        settingsMap["indent-string"] = newValue
                    } else {
                        settingsMap.remove("indent-string")
                    }
                }
            }

            entries.add(SettingsEntry("Indent Size", component, "Number of spaces to use for each indentation level."))
        }

        entries.sortBy { it.name }
    }
}
