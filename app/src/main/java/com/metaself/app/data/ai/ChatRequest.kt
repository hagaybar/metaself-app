package com.metaself.app.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The one place a request is put together from what is asked and how it is sent (D57 §1).
 *
 * What is asked — the messages and the reply's schema — is [EstimatePrompt]'s and [ReviewPrompt]'s.
 * How it is sent is the model's [RequestProfile]. Keeping the two apart is what lets a refusal
 * change how a request is sent without touching what it says.
 */
object ChatRequest {

    /** One message: `system` for the app's instructions, `user` for what is asked about. */
    data class Message(val role: String, val content: String)

    /**
     * The request body.
     *
     * With [RequestProfile.strictFormat] the reply is pinned by a strict `json_schema`. Without it
     * the reply is asked for as a `json_object` and the schema is written after the first system
     * message's instructions — the same text the strict format carries, moved, and nothing else
     * added. The replies are checked field by field on the phone either way.
     */
    fun body(
        model: String,
        profile: RequestProfile,
        messages: List<Message>,
        schemaName: String,
        schema: JsonObject,
    ): String {
        val firstSystem = messages.indexOfFirst { it.role == "system" }
        val sent = if (profile.strictFormat) {
            messages
        } else {
            messages.mapIndexed { index, message ->
                if (index == firstSystem) {
                    message.copy(content = message.content + schemaInstruction(schema))
                } else {
                    message
                }
            }
        }
        return buildJsonObject {
            put("model", model)
            if (profile.temperature) put("temperature", 0)
            profile.reasoningEffort?.let { put("reasoning_effort", it) }
            putJsonArray("messages") {
                sent.forEach { message ->
                    add(
                        buildJsonObject {
                            put("role", message.role)
                            put("content", message.content)
                        },
                    )
                }
            }
            putJsonObject("response_format") {
                if (profile.strictFormat) {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", schemaName)
                        put("strict", true)
                        put("schema", schema)
                    }
                } else {
                    put("type", "json_object")
                }
            }
        }.toString()
    }

    /** The schema as an instruction, for a model that takes no strict format. Says "JSON", as that mode requires. */
    private fun schemaInstruction(schema: JsonObject): String =
        "\n\nReply with one JSON object, and nothing else, that follows this JSON schema exactly, " +
            "with every property it lists:\n$schema"
}
