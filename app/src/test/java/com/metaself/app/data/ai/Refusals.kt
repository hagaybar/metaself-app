package com.metaself.app.data.ai

/**
 * Refusal bodies shaped like the provider's real error objects (D57 §3). Invented: the wording
 * follows the provider's pattern, and no model or key named here is real.
 */
object Refusals {

    const val TEMPERATURE = """{"error":{"message":"Unsupported value: 'temperature' does not support 0 with this model. Only the default (1) value is supported.","type":"invalid_request_error","param":"temperature","code":"unsupported_value"}}"""

    const val EFFORT_LOW_WITH_LIST = """{"error":{"message":"Unsupported value: 'reasoning_effort' does not support 'low' with this model. Supported values are: 'medium' and 'high'.","type":"invalid_request_error","param":"reasoning_effort","code":"unsupported_value"}}"""

    const val EFFORT_PARAMETER = """{"error":{"message":"Unsupported parameter: 'reasoning_effort' is not supported with this model.","type":"invalid_request_error","param":"reasoning_effort","code":"unsupported_parameter"}}"""

    const val MAX_TOKENS = """{"error":{"message":"Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.","type":"invalid_request_error","param":"max_tokens","code":"unsupported_parameter"}}"""

    const val RESPONSE_FORMAT = """{"error":{"message":"Invalid parameter: 'response_format' of type 'json_schema' is not supported with this model.","type":"invalid_request_error","param":"response_format","code":null}}"""

    /** The app's own schema refused — true of every model, and nothing to learn from. */
    const val SCHEMA_INVALID = """{"error":{"message":"Invalid schema for response_format 'food_review': In context=(), 'required' is required to be supplied and to be an array including every key in properties. Missing 'note'.","type":"invalid_request_error","param":"response_format","code":null}}"""

    /** The same, with the code the provider gives it. */
    const val SCHEMA_INVALID_CODED = """{"error":{"message":"Invalid schema for response_format 'meal_estimate': schema must be a JSON Schema of 'type: \"object\"', got 'type: \"array\"'.","type":"invalid_request_error","param":"response_format","code":"invalid_json_schema"}}"""

    /** An argument the model does not know, named without quotes and with no `param`. */
    const val EFFORT_UNRECOGNISED = """{"error":{"message":"Unrecognized request argument supplied: reasoning_effort","type":"invalid_request_error","param":null,"code":null}}"""

    const val UNKNOWN = """{"error":{"message":"The model 'an-invented-model' does not exist or you do not have access to it.","type":"invalid_request_error","param":null,"code":"model_not_found"}}"""

    /** A value refused with no list of accepted ones, and no `param`: only the message says which. */
    fun effortWithoutList(value: String) =
        """{"error":{"message":"Unsupported value: 'reasoning_effort' does not support '$value' with this model.","type":"invalid_request_error","param":null,"code":null}}"""
}
