package com.ufi.android.data.model

import org.json.JSONArray
import org.json.JSONObject

object ToolDefinitions {

    fun getGeminiTools(): JSONArray {
        val definitions = listOf(
            functionDeclaration("displayText") {
                desc("Show detailed text on screen. Use for: search results, code, links, and detailed information.")
                param("text", "string", "Detailed text content to display on screen")
            },
            functionDeclaration("displayCard") {
                desc("Show a rich card on screen: code, info, quote, alert, link")
                param("type", "string", "Card type: code, info, quote, alert, link",
                    enum = listOf("code", "info", "quote", "alert", "link"))
                param("data", "object", "Card data. For code: { code, language }. For link: { title, url, description }")
            },
            functionDeclaration("showImage") {
                desc("Display an image in the chat. Use when user asks to see a photo.")
                param("source", "string", "Image URL (https://...)")
            },
            functionDeclaration("webSearch") {
                desc("Search the web for current information. Use for facts, news, and up-to-date data.")
                param("query", "string", "Search query")
            },
            functionDeclaration("weather") {
                desc("Get current weather for a location.")
                param("location", "string", "City name or coordinates")
            },
            functionDeclaration("setReminder") {
                desc("Set a reminder for a specific date/time.")
                param("text", "string", "Reminder text")
                param("datetime", "string", "ISO 8601 datetime (e.g. 2026-05-22T15:00)")
            },
            functionDeclaration("listReminders") {
                desc("List all active reminders.")
            },
            functionDeclaration("deleteReminder") {
                desc("Delete a reminder by ID or text keyword.")
                param("id", "string", "Reminder ID")
                param("text", "string", "Delete reminders matching this text keyword")
            },
            functionDeclaration("mediaPlay") {
                desc("Resume playback of paused media.")
            },
            functionDeclaration("mediaPause") {
                desc("Pause currently playing media.")
            },
            functionDeclaration("mediaStop") {
                desc("Stop currently playing media entirely.")
            },
            functionDeclaration("mediaNext") {
                desc("Skip to next track/video.")
            },
            functionDeclaration("mediaPrevious") {
                desc("Go to previous track/video.")
            },
            functionDeclaration("mediaVolumeUp") {
                desc("Increase media volume by 10%.")
            },
            functionDeclaration("mediaVolumeDown") {
                desc("Decrease media volume by 10%.")
            },
            functionDeclaration("deepResearch") {
                desc("Deep search: Wikipedia + news + web. Returns structured report.")
                param("topic", "string", "Topic to research")
            },
            functionDeclaration("repoSearch") {
                desc("Search GitHub repositories by name/keyword.")
                param("name", "string", "Repository name or keyword")
                param("limit", "integer", "Max results (1-10)", default = 5)
            },
        )

        val result = JSONArray()
        val functions = JSONArray()
        definitions.forEach { functions.put(it) }

        val wrapper = JSONObject()
        wrapper.put("functionDeclarations", functions)
        result.put(wrapper)
        return result
    }

    private fun functionDeclaration(name: String, block: FunctionDeclarationBuilder.() -> Unit): JSONObject {
        val builder = FunctionDeclarationBuilder(name)
        builder.block()
        return builder.build()
    }

    class FunctionDeclarationBuilder(private val name: String) {
        private var description: String = ""
        private val properties = JSONObject()
        private val required = JSONArray()

        fun desc(text: String) { description = text }

        fun param(name: String, type: String, description: String, enum: List<String>? = null, default: Any? = null) {
            val obj = JSONObject()
            obj.put("type", type.uppercase())
            obj.put("description", description)
            if (enum != null) {
                val enumArr = JSONArray()
                enum.forEach { enumArr.put(it) }
                obj.put("enum", enumArr)
            }
            properties.put(name, obj)
            required.put(name)
        }

        fun build(): JSONObject {
            val obj = JSONObject()
            obj.put("name", name)
            obj.put("description", description)
            val parameters = JSONObject()
            parameters.put("type", "OBJECT")
            parameters.put("properties", properties)
            parameters.put("required", required)
            obj.put("parameters", parameters)
            return obj
        }
    }
}
