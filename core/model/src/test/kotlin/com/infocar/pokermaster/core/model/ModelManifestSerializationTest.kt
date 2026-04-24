package com.infocar.pokermaster.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class ModelManifestSerializationTest {

    private val json = Json { encodeDefaults = true; prettyPrint = false }

    private fun sample(): ModelManifest = ModelManifest(
        version = 1,
        generatedAtEpochSec = 1_745_000_000L,
        models = listOf(
            ModelEntry(
                id = "llama-3.2-1b-q4km",
                displayName = "Llama 3.2 1B (Q4_K_M)",
                family = ModelFamily.LLAMA,
                fileName = "llama-3.2-1b-q4_k_m.gguf",
                sizeBytes = 732_160_000L,
                sha256 = "0".repeat(64),
                primaryUrl = "https://huggingface.co/example/Llama-3.2-1B-GGUF/resolve/main/llama-3.2-1b-q4_k_m.gguf",
                fallbackUrls = listOf(
                    "https://models.pokermaster.example/llama-3.2-1b-q4_k_m.gguf",
                    "https://github.com/example/pokermaster-models/releases/download/v1/llama-3.2-1b-q4_k_m.gguf",
                ),
                license = ModelLicense.LLAMA_COMMUNITY,
                attributionNotice = "Built with Llama",
            ),
            ModelEntry(
                id = "qwen-2.5-1.5b-q4km",
                displayName = "Qwen2.5 1.5B (Q4_K_M)",
                family = ModelFamily.QWEN,
                fileName = "qwen-2.5-1.5b-q4_k_m.gguf",
                sizeBytes = 986_000_000L,
                sha256 = "a".repeat(64),
                primaryUrl = "https://huggingface.co/example/Qwen2.5-1.5B-GGUF/resolve/main/qwen-2.5-1.5b-q4_k_m.gguf",
                license = ModelLicense.APACHE_2_0,
            ),
        ),
    )

    @Test
    fun `manifest round-trips losslessly`() {
        val original = sample()
        val encoded = json.encodeToString(ModelManifest.serializer(), original)
        val decoded = json.decodeFromString(ModelManifest.serializer(), encoded)
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `default fields populate when absent in json`() {
        val minimal = """
            {
              "version": 1,
              "generatedAtEpochSec": 100,
              "models": [
                {
                  "id": "qwen",
                  "displayName": "Qwen",
                  "family": "QWEN",
                  "fileName": "qwen.gguf",
                  "sizeBytes": 1,
                  "sha256": "aa",
                  "primaryUrl": "https://example/qwen.gguf",
                  "license": "APACHE_2_0"
                }
              ]
            }
        """.trimIndent()
        val decoded = json.decodeFromString(ModelManifest.serializer(), minimal)
        val entry = decoded.models.single()
        assertThat(entry.fallbackUrls).isEmpty()
        assertThat(entry.requiredContextTokens).isEqualTo(2048)
        assertThat(entry.attributionNotice).isNull()
    }

    @Test
    fun `unknown fields are rejected under strict default`() {
        // 기본 Json 은 unknown key 를 오류로 낸다 — manifest 진본성 방어.
        val withUnknown = """
            {
              "version": 1,
              "generatedAtEpochSec": 0,
              "models": [],
              "rogueField": "nope"
            }
        """.trimIndent()
        val strict = Json {}
        val thrown = runCatching { strict.decodeFromString(ModelManifest.serializer(), withUnknown) }
        assertThat(thrown.isFailure).isTrue()
    }
}
