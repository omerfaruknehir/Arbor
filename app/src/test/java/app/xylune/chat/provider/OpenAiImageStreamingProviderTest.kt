package app.xylune.chat.provider

import app.xylune.chat.data.AttachmentEntity
import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ModelEntity
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenAiImageStreamingProviderTest {
    private val provider = DefaultCatalog.providers.single { it.id == "openai" }
    private val model = DefaultCatalog.models.single { it.providerId == "openai" && it.modelId == "gpt-image-2" }
    private val transport = OpenAiImageStreamingProvider(OpenAiCompatibleProvider())

    @Test
    fun `generation requests native streaming partial images`() {
        val request = transport.buildGenerationRequest(request(), "A moonlit city")
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val json = buffer.readUtf8()

        assertEquals("https://api.openai.com/v1/images/generations", request.url.toString())
        assertEquals("text/event-stream", request.header("Accept"))
        assertTrue(json.contains("\"model\":\"gpt-image-2\""))
        assertTrue(json.contains("\"stream\":true"))
        assertTrue(json.contains("\"partial_images\":3"))
        assertTrue(json.contains("\"output_format\":\"png\""))
    }

    @Test
    fun `edit requests use multipart image array and streaming`() {
        val file = File.createTempFile("xylune-image-edit", ".png")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val attachment = AttachmentEntity(
                id = "ref",
                conversationId = "conversation",
                messageNodeId = null,
                displayName = "reference.png",
                mimeType = "image/png",
                sizeBytes = file.length(),
                localPath = file.absolutePath,
                createdAt = 1L,
            )
            val request = transport.buildEditRequest(request(), "Make it nocturnal", listOf(attachment))
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            val multipart = buffer.readUtf8()

            assertEquals("https://api.openai.com/v1/images/edits", request.url.toString())
            assertEquals("text/event-stream", request.header("Accept"))
            assertTrue(request.body!!.contentType().toString().startsWith("multipart/form-data"))
            assertTrue(multipart.contains("name=\"image[]\""))
            assertTrue(multipart.contains("filename=\"reference.png\""))
            assertTrue(multipart.contains("name=\"stream\""))
            assertTrue(multipart.contains("true"))
            assertTrue(multipart.contains("name=\"partial_images\""))
            assertTrue(multipart.contains("3"))
        } finally {
            file.delete()
        }
    }

    private fun request() = ChatRequest(
        provider = provider,
        model = model,
        apiKey = "test-key",
        messages = listOf(InputMessage(MessageRole.USER, "Create an image")),
        maxOutputTokens = 1,
        thinkingEnabled = false,
    )
}
