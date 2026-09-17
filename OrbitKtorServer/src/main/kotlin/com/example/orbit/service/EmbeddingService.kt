package com.example.orbit.service

import com.google.genai.Client
import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import com.google.genai.types.EmbedContentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(EmbeddingService::class.java)

/** Model za vektore; menja se preko GEMINI_EMBEDDING_MODEL */
private const val DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001"

/** Kraci vektor od podrazumevanog; dovoljno za nekoliko hiljada dogadjaja */
const val EMBEDDING_DIMENSIONS = 768

/** Dokument i upit se embeduju razlicito, to model ocekuje */
private const val TASK_DOCUMENT = "RETRIEVAL_DOCUMENT"
private const val TASK_QUERY = "RETRIEVAL_QUERY"

/** Koliko tekstova ide u jednom pozivu pri popunjavanju */
const val EMBEDDING_BATCH_SIZE = 50

/**
 * F-32: pretvara tekst u vektor preko Gemini-ja; kljuc ostaje na serveru.
 * Svaka greska je null, pozivalac se vraca na pretragu po recima.
 */
class EmbeddingService(apiKey: String?, private val model: String) {

    private val client: Client? = apiKey?.let { Client.builder().apiKey(it).build() }

    val isConfigured: Boolean get() = client != null

    /** Vektori naslova i opisa; pozicija u listi prati ulaz, null za neuspeh */
    suspend fun embedDocuments(texts: List<String>): List<List<Float>?> {
        if (texts.isEmpty()) return emptyList()
        return texts.chunked(EMBEDDING_BATCH_SIZE)
            .flatMap { chunk -> embed(chunk, TASK_DOCUMENT) ?: List(chunk.size) { null } }
    }

    /** Vektor korisnickog upita; null kad model nije dostupan */
    suspend fun embedQuery(text: String): List<Float>? =
        embed(listOf(text), TASK_QUERY)?.firstOrNull()

    private suspend fun embed(texts: List<String>, taskType: String): List<List<Float>?>? {
        val client = client ?: return null

        val config = EmbedContentConfig.builder()
            .taskType(taskType)
            .outputDimensionality(EMBEDDING_DIMENSIONS)
            .build()

        // SDK poziv blokira, zato Dispatchers.IO
        return withContext(Dispatchers.IO) {
            try {
                val embeddings = client.models.embedContent(model, texts, config)
                    .embeddings()
                    .orElse(null)
                    ?: return@withContext null.also { log.warn("Gemini returned no embeddings") }

                texts.indices.map { index ->
                    embeddings.getOrNull(index)?.values()?.orElse(null)
                }
            } catch (e: ApiException) {
                log.warn("Embedding API error ${e.code()}: ${e.message()}")
                null
            } catch (e: GenAiIOException) {
                log.warn("Could not reach Gemini for embeddings", e)
                null
            }
        }
    }

    companion object {
        fun fromEnvironment(): EmbeddingService = EmbeddingService(
            apiKey = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() },
            model = System.getenv("GEMINI_EMBEDDING_MODEL")?.takeIf { it.isNotBlank() }
                ?: DEFAULT_EMBEDDING_MODEL,
        )
    }
}
