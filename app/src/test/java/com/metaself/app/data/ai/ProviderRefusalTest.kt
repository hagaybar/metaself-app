package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Reading the provider's error object (D57 §3): structured fields first, the message only where they are silent. */
class ProviderRefusalTest {

    @Test
    fun `a refused temperature is read from its structured fields`() {
        val refusal = ProviderRefusal.parse(Refusals.TEMPERATURE)!!

        assertThat(refusal.type).isEqualTo("invalid_request_error")
        assertThat(refusal.code).isEqualTo("unsupported_value")
        assertThat(refusal.parameter).isEqualTo("temperature")
        assertThat(refusal.message).startsWith("Unsupported value: 'temperature' does not support 0")
        assertThat(refusal.supportedValues).isEmpty()
    }

    @Test
    fun `a refused reasoning value carries the accepted values in the message's order`() {
        val refusal = ProviderRefusal.parse(Refusals.EFFORT_LOW_WITH_LIST)!!

        assertThat(refusal.parameter).isEqualTo("reasoning_effort")
        assertThat(refusal.code).isEqualTo("unsupported_value")
        assertThat(refusal.supportedValues).containsExactly("medium", "high").inOrder()
    }

    @Test
    fun `a list written with an Oxford comma is read whole`() {
        val body = Refusals.EFFORT_LOW_WITH_LIST
            .replace("'medium' and 'high'", "'none', 'medium', and 'high'")

        assertThat(ProviderRefusal.parse(body)!!.supportedValues)
            .containsExactly("none", "medium", "high").inOrder()
    }

    @Test
    fun `an unsupported token limit is read, for nothing to fix`() {
        val refusal = ProviderRefusal.parse(Refusals.MAX_TOKENS)!!

        assertThat(refusal.parameter).isEqualTo("max_tokens")
        assertThat(refusal.code).isEqualTo("unsupported_parameter")
        assertThat(refusal.supportedValues).isEmpty()
    }

    @Test
    fun `a refused reply format has no code and is read by its param`() {
        val refusal = ProviderRefusal.parse(Refusals.RESPONSE_FORMAT)!!

        assertThat(refusal.parameter).isEqualTo("response_format")
        assertThat(refusal.code).isNull()
    }

    @Test
    fun `with no param, the parameter is the first one the message quotes`() {
        val refusal = ProviderRefusal.parse(Refusals.effortWithoutList("minimal"))!!

        assertThat(refusal.parameter).isEqualTo("reasoning_effort")
        assertThat(refusal.code).isNull()
    }

    @Test
    fun `with no param and nothing quoted, a known parameter named in the message is found`() {
        assertThat(ProviderRefusal.parse(Refusals.EFFORT_UNRECOGNISED)!!.parameter).isEqualTo("reasoning_effort")
        val body = Refusals.EFFORT_UNRECOGNISED.replace("reasoning_effort", "max_completion_tokens")
        assertThat(ProviderRefusal.parse(body)!!.parameter).isEqualTo("max_completion_tokens")
    }

    @Test
    fun `a refusal of the schema is read as about the reply format`() {
        listOf(Refusals.SCHEMA_INVALID, Refusals.SCHEMA_INVALID_CODED).forEach {
            assertThat(ProviderRefusal.parse(it)!!.parameter).isEqualTo("response_format")
        }
        assertThat(ProviderRefusal.parse(Refusals.SCHEMA_INVALID_CODED)!!.code).isEqualTo("invalid_json_schema")
    }

    @Test
    fun `an unknown refusal names no parameter`() {
        val refusal = ProviderRefusal.parse(Refusals.UNKNOWN)!!

        assertThat(refusal.code).isEqualTo("model_not_found")
        assertThat(refusal.parameter).isNull()
    }

    @Test
    fun `a body that is not an error object is no refusal, and nothing throws`() {
        listOf("", "not json", "[]", """{"choices":[]}""", """{"error":"a string"}""").forEach {
            assertThat(ProviderRefusal.parse(it)).isNull()
        }
    }
}
