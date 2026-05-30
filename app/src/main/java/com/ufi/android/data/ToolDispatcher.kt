package com.ufi.android.data

import android.util.Log
import com.ufi.android.data.model.ChatMessage
import com.ufi.android.data.model.MessageRole
import com.ufi.android.data.websocket.ToolCallInfo
import com.ufi.android.data.websocket.ToolResponseItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class ToolDispatcher(
    private val scope: CoroutineScope,
    private val onDisplayText: (String) -> Unit,
    private val onDisplayCard: (String, JSONObject) -> Unit,
    private val onShowImage: (String) -> Unit,
) {
    companion object {
        private const val TAG = "ToolDispatcher"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    var serverUrl: String = ""

    fun dispatch(toolCalls: List<ToolCallInfo>, callback: (List<ToolResponseItem>) -> Unit) {
        scope.launch {
            val responses = mutableListOf<ToolResponseItem>()
            for (tc in toolCalls) {
                val result = handleToolCall(tc)
                responses.add(ToolResponseItem(tc.id, tc.name, result))
            }
            withContext(Dispatchers.Main) {
                callback(responses)
            }
        }
    }

    private suspend fun handleToolCall(tc: ToolCallInfo): Any {
        return when (tc.name) {
            "displayText" -> handleDisplayText(tc.args)
            "displayCard" -> handleDisplayCard(tc.args)
            "showImage" -> handleShowImage(tc.args)
            "webSearch" -> handleWebSearch(tc.args)
            "weather" -> handleWeather(tc.args)
            "setReminder" -> handleSetReminder(tc.args)
            "listReminders" -> handleListReminders()
            "deleteReminder" -> handleDeleteReminder(tc.args)
            "mediaPlay", "mediaPause", "mediaStop", "mediaNext", "mediaPrevious",
            "mediaVolumeUp", "mediaVolumeDown" -> handleMediaCommand(tc.name)
            "deepResearch" -> handleDeepResearch(tc.args)
            "repoSearch" -> handleRepoSearch(tc.args)
            else -> handleProxyCommand(tc.name, tc.args)
        }
    }

    private fun handleDisplayText(args: JSONObject): JSONObject {
        val text = args.optString("text", "")
        onDisplayText(text)
        return JSONObject().put("status", "displayed")
    }

    private fun handleDisplayCard(args: JSONObject): JSONObject {
        val type = args.optString("type", "info")
        val data = args.optJSONObject("data") ?: JSONObject()
        onDisplayCard(type, data)
        return JSONObject().put("status", "displayed")
    }

    private fun handleShowImage(args: JSONObject): JSONObject {
        val source = args.optString("source", "")
        onShowImage(source)
        return JSONObject().put("status", "displayed")
    }

    private suspend fun handleWebSearch(args: JSONObject): JSONObject {
        val query = args.optString("query", "")
        if (serverUrl.isNotBlank()) {
            return proxyToServer("webSearch", JSONObject().put("query", query))
        }
        return duckDuckGoSearch(query)
    }

    private suspend fun handleWeather(args: JSONObject): JSONObject {
        val location = args.optString("location", "Tashkent")
        if (serverUrl.isNotBlank()) {
            return proxyToServer("weather", JSONObject().put("location", location))
        }
        return JSONObject().put("result", "Weather service requires server connection")
    }

    private fun handleSetReminder(args: JSONObject): JSONObject {
        val text = args.optString("text", "")
        val datetime = args.optString("datetime", "")
        Log.d(TAG, "Reminder set: $text at $datetime")
        return JSONObject().put("status", "reminder_set").put("text", text).put("datetime", datetime)
    }

    private fun handleListReminders(): JSONObject {
        return JSONObject().put("reminders", JSONArray())
    }

    private fun handleDeleteReminder(args: JSONObject): JSONObject {
        return JSONObject().put("status", "deleted")
    }

    private fun handleMediaCommand(command: String): JSONObject {
        if (serverUrl.isNotBlank()) {
            return runBlockingProxy("mediaCommand", JSONObject().put("command", command))
        }
        return JSONObject().put("result", "Media control requires server connection")
    }

    private suspend fun handleDeepResearch(args: JSONObject): JSONObject {
        if (serverUrl.isNotBlank()) {
            return proxyToServer("deepResearch", args)
        }
        return JSONObject().put("result", "Deep research requires server connection")
    }

    private suspend fun handleRepoSearch(args: JSONObject): JSONObject {
        val name = args.optString("name", "")
        val limit = args.optInt("limit", 5)
        return try {
            searchGitHub(name, limit)
        } catch (e: Exception) {
            Log.e(TAG, "GitHub search error", e)
            JSONObject().put("error", e.message)
        }
    }

    private fun handleProxyCommand(name: String, args: JSONObject): JSONObject {
        if (serverUrl.isNotBlank()) {
            return runBlockingProxy(name, args)
        }
        return JSONObject().put("result", "Tool '$name' requires server connection")
    }

    private suspend fun proxyToServer(command: String, args: JSONObject): JSONObject {
        return withContext(Dispatchers.IO) {
            runBlockingProxy(command, args)
        }
    }

    private fun runBlockingProxy(command: String, args: JSONObject): JSONObject {
        if (serverUrl.isBlank()) {
            return JSONObject().put("error", "Server URL not configured")
        }
        return try {
            val url = URL("$serverUrl/api/exec")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 30000

            val body = JSONObject()
            body.put("command", command)
            body.put("args", JSONArray())
            body.put("background", false)
            conn.outputStream.write(body.toString().toByteArray())

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            conn.disconnect()
            JSONObject(response)
        } catch (e: Exception) {
            Log.e(TAG, "Proxy error for $command", e)
            JSONObject().put("error", e.message)
        }
    }

    private suspend fun duckDuckGoSearch(query: String): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(response)
                val results = JSONArray()
                val abstractText = json.optString("AbstractText", "")
                val abstractSource = json.optString("AbstractSource", "")
                if (abstractText.isNotBlank()) {
                    val result = JSONObject()
                    result.put("title", abstractSource)
                    result.put("snippet", abstractText)
                    result.put("url", json.optString("AbstractURL", ""))
                    results.put(result)
                }

                val related = json.optJSONArray("RelatedTopics") ?: JSONArray()
                for (i in 0 until related.length().coerceAtMost(5)) {
                    val topic = related.getJSONObject(i)
                    if (topic.has("Text")) {
                        val result = JSONObject()
                        result.put("title", topic.optString("FirstURL", ""))
                        result.put("snippet", topic.optString("Text", ""))
                        result.put("url", topic.optString("FirstURL", ""))
                        results.put(result)
                    }
                }

                JSONObject().put("results", results)
            } catch (e: Exception) {
                Log.e(TAG, "DuckDuckGo error", e)
                JSONObject().put("error", e.message)
            }
        }
    }

    private suspend fun searchGitHub(query: String, limit: Int): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/search/repositories?q=${java.net.URLEncoder.encode(query, "UTF-8")}&per_page=$limit&sort=stars&order=desc")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(response)
                val items = json.optJSONArray("items") ?: JSONArray()
                val results = JSONArray()

                for (i in 0 until items.length().coerceAtMost(limit)) {
                    val item = items.getJSONObject(i)
                    val result = JSONObject()
                    result.put("name", item.optString("full_name", ""))
                    result.put("description", item.optString("description", ""))
                    result.put("stars", item.optInt("stargazers_count", 0))
                    result.put("language", item.optString("language", ""))
                    result.put("url", item.optString("html_url", ""))
                    results.put(result)
                }

                JSONObject().put("repositories", results)
            } catch (e: Exception) {
                Log.e(TAG, "GitHub search error", e)
                JSONObject().put("error", e.message)
            }
        }
    }
}
