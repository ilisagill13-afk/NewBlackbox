package com.usvisa.slotbooker.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.OutputConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Snapshot of the polling situation handed to the model. */
data class AiContext(
    val checksThisHour: Int,
    val hourlyBudget: Int,
    val consecutiveErrors: Int,
    val lastOutcome: String,
    val sawSlotRecently: Boolean,
    val localTime: String,
    val minDate: String,
    val maxDate: String,
    val minSeconds: Int,
    val maxSeconds: Int
)

data class AiDecision(val nextCheckSeconds: Int, val reasoning: String)

/**
 * The real AI: calls Claude (official Anthropic Java SDK) to decide how long to wait before the
 * next slot check. Returns null on any failure so the caller can fall back to the rule-based
 * controller. The model's answer is still clamped by [AdaptiveIntervalController] for safety.
 */
class AiIntervalAdvisor(apiKey: String) {

    private val client: AnthropicClient =
        AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    suspend fun decide(ctx: AiContext): AiDecision? = withContext(Dispatchers.IO) {
        try {
            val params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_8)
                .maxTokens(512L)
                // Low effort keeps this lightweight scheduling decision fast and cheap.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(buildPrompt(ctx))
                .build()

            val message = client.messages().create(params)
            val text = StringBuilder().apply {
                message.content().forEach { block -> block.text().ifPresent { append(it.text()) } }
            }.toString()
            parse(text, ctx)
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(text: String, ctx: AiContext): AiDecision? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val obj = JSONObject(text.substring(start, end + 1))
            val secs = obj.optInt("next_check_seconds", -1)
            if (secs < 0) return null
            AiDecision(
                nextCheckSeconds = secs.coerceIn(ctx.minSeconds, ctx.maxSeconds),
                reasoning = obj.optString("reasoning").ifBlank { "no reason given" }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun buildPrompt(ctx: AiContext): String = """
        You schedule polls of the US visa appointment website to grab the earliest open slot
        without tripping its anti-bot rate limiter. Decide how many seconds to wait before the
        NEXT check.

        Hard bounds (respect them): between ${ctx.minSeconds} and ${ctx.maxSeconds} seconds.
        Guidance:
        - Stay well under the hourly budget of ${ctx.hourlyBudget} checks; ${ctx.checksThisHour} already used this hour.
        - If a slot appeared or slots seem to be churning, check sooner; if it's quiet, you may ease off a little.
        - Consecutive errors so far: ${ctx.consecutiveErrors}. Last check outcome: ${ctx.lastOutcome}. Saw a slot recently: ${ctx.sawSlotRecently}.
        - Local time now: ${ctx.localTime}. Target date window: ${ctx.minDate}..${ctx.maxDate}.

        Respond with ONLY a JSON object and nothing else:
        {"next_check_seconds": <integer>, "reasoning": "<one short sentence>"}
    """.trimIndent()
}
