package com.pumpernickel.data.api

import com.pumpernickel.domain.ai.AiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 22 Plan 10 / D-22-13 — unit-tests against the SSE parser extracted from
 * [com.pumpernickel.data.api.AnthropicClient.chatCompletionStreaming]. Sample
 * frames are derived from https://docs.anthropic.com/en/api/messages-streaming.
 *
 * Tested behaviours:
 *  - text_delta accumulation across multiple frames
 *  - message_stop sets [AnthropicSseParser.done]
 *  - typed error mapping: authentication_error → AuthOrQuota,
 *    overloaded_error → Provider, others → SchemaInvalid
 *  - silent ignore of message_start / content_block_start / message_delta / ping
 */
class AnthropicSseParserTest {

    @Test
    fun feed_singleContentBlockDelta_emitsText() {
        val p = AnthropicSseParser()
        p.feed("event: content_block_delta")
        val r = p.feed(
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""
        )
        assertEquals("Hello", r)
    }

    @Test
    fun feed_multipleContentBlockDelta_concatenates() {
        val p = AnthropicSseParser()
        p.feed("event: content_block_delta")
        p.feed(
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello "}}"""
        )
        // Blank line marks SSE event boundary; the next event-line resets the parser's currentEventType.
        p.feed("")
        p.feed("event: content_block_delta")
        val r = p.feed(
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"world"}}"""
        )
        assertEquals("Hello world", r)
        assertEquals("Hello world", p.result())
    }

    @Test
    fun feed_messageStop_setsDone() {
        val p = AnthropicSseParser()
        p.feed("event: message_stop")
        val r = p.feed("""data: {"type":"message_stop"}""")
        // message_stop frames produce no content update.
        assertNull(r)
        assertTrue(p.done)
    }

    @Test
    fun feed_authenticationError_throwsAuthOrQuota() {
        val p = AnthropicSseParser()
        p.feed("event: error")
        val ex = assertFailsWith<AiError.AuthOrQuota> {
            p.feed(
                """data: {"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
            )
        }
        assertEquals(401, ex.httpStatus)
    }

    @Test
    fun feed_overloadedError_throwsProvider() {
        val p = AnthropicSseParser()
        p.feed("event: error")
        val ex = assertFailsWith<AiError.Provider> {
            p.feed(
                """data: {"type":"error","error":{"type":"overloaded_error","message":"overloaded"}}"""
            )
        }
        assertEquals(529, ex.httpStatus)
    }

    @Test
    fun feed_invalidRequestError_throwsSchemaInvalid() {
        val p = AnthropicSseParser()
        p.feed("event: error")
        val ex = assertFailsWith<AiError.SchemaInvalid> {
            p.feed(
                """data: {"type":"error","error":{"type":"invalid_request_error","message":"bad request"}}"""
            )
        }
        // The detail message should carry the upstream message for diagnostics.
        assertTrue(ex.detail.contains("bad request"))
    }

    @Test
    fun feed_pingAndMessageStart_silentlyIgnored() {
        val p = AnthropicSseParser()
        p.feed("event: ping")
        assertNull(p.feed("""data: {"type":"ping"}"""))
        p.feed("")  // event boundary
        p.feed("event: message_start")
        assertNull(
            p.feed("""data: {"type":"message_start","message":{"id":"msg_1"}}""")
        )
        assertFalse(p.done)
        assertEquals("", p.result())
    }

    @Test
    fun feed_contentBlockStartAndStop_silentlyIgnored() {
        val p = AnthropicSseParser()
        // Anthropic emits content_block_start / content_block_stop around every
        // content_block_delta — parser must drop them without error.
        p.feed("event: content_block_start")
        assertNull(
            p.feed("""data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
        )
        p.feed("")
        p.feed("event: content_block_stop")
        assertNull(p.feed("""data: {"type":"content_block_stop","index":0}"""))
        assertEquals("", p.result())
    }
}
