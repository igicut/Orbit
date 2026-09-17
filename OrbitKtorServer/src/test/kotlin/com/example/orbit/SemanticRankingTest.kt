package com.example.orbit

import com.example.orbit.model.EventCategory
import com.example.orbit.model.ExposedEvent
import com.example.orbit.model.Visibility
import com.example.orbit.service.SemanticRanking
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F-32: matematika rangiranja, bez Gemini-ja i bez baze */
class SemanticRankingTest {

    private fun event(id: String, title: String, startTime: Long = 0L) = ExposedEvent(
        id = id,
        ownerId = "owner",
        title = title,
        description = "",
        latitude = 44.8,
        longitude = 20.4,
        startTime = startTime,
        category = EventCategory.OTHER,
        visibility = Visibility.PUBLIC,
    )

    @Test
    fun `identical vectors give 1 and opposite give -1`() {
        val vector = listOf(1f, 2f, 3f)
        assertEquals(1.0, SemanticRanking.cosineSimilarity(vector, vector), 1e-9)
        assertEquals(-1.0, SemanticRanking.cosineSimilarity(vector, vector.map { -it }), 1e-9)
    }

    @Test
    fun `cosine ignores magnitude but a dot product would not`() {
        val query = listOf(1f, 1f)
        val short = listOf(1f, 1f)
        val long = listOf(100f, 100f)

        // Oba pokazuju u istom smeru, pa je slicnost ista bez obzira na duzinu
        assertEquals(1.0, SemanticRanking.cosineSimilarity(short, query), 1e-9)
        assertEquals(1.0, SemanticRanking.cosineSimilarity(long, query), 1e-9)
    }

    @Test
    fun `orthogonal vectors give 0 and known angle is exact`() {
        assertEquals(0.0, SemanticRanking.cosineSimilarity(listOf(1f, 0f), listOf(0f, 1f)), 1e-9)
        // 45 stepeni izmedju (1,0) i (1,1)
        assertEquals(
            1.0 / sqrt(2.0),
            SemanticRanking.cosineSimilarity(listOf(1f, 0f), listOf(1f, 1f)),
            1e-9,
        )
    }

    @Test
    fun `empty or mismatched vectors give 0 instead of failing`() {
        assertEquals(0.0, SemanticRanking.cosineSimilarity(emptyList(), emptyList()))
        assertEquals(0.0, SemanticRanking.cosineSimilarity(listOf(1f, 2f), listOf(1f)))
        assertEquals(0.0, SemanticRanking.cosineSimilarity(listOf(0f, 0f), listOf(1f, 1f)))
    }

    @Test
    fun `keyword hit without a vector is kept at the end`() {
        val hit = event("keyword", "Hakaton")
        val related = event("related", "Programerski maraton")
        val filler = (1..3).map { event("filler$it", "Nevezan $it") }

        val ranked = SemanticRanking.rank(
            candidates = listOf(hit, related) + filler,
            keywordHits = setOf("keyword"),
            vectors = mapOf("related" to listOf(1f, 0f)) +
                filler.associate { it.id to listOf(0f, 1f) },
            queryVector = listOf(1f, 0f),
        )

        assertEquals(listOf("related", "keyword"), ranked.map { it.id })
        assertEquals(1.0, ranked.first().relevance!!, 1e-9)
        assertNull(ranked.last().relevance)
    }

    @Test
    fun `a single unrelated event is left out`() {
        val ranked = SemanticRanking.rank(
            candidates = listOf(event("far", "Nesto deseto")),
            keywordHits = emptySet(),
            vectors = mapOf("far" to listOf(0f, 1f)),
            queryVector = listOf(1f, 0f),
        )

        assertTrue(ranked.isEmpty())
    }

    /**
     * Ovo je greska nadjena u merenju: besmislena rec dobija 0.58-0.63 nad svim dogadjajima,
     * pa bi apsolutni prag vratio ceo katalog.
     */
    @Test
    fun `a flat field of similarities adds nothing`() {
        val candidates = (1..5).map { event("e$it", "Event $it") }
        val vectors = mapOf(
            "e1" to listOf(1.000f, 0.780f),
            "e2" to listOf(1.000f, 0.783f),
            "e3" to listOf(1.000f, 0.785f),
            "e4" to listOf(1.000f, 0.788f),
            "e5" to listOf(1.000f, 0.790f),
        )

        val ranked = SemanticRanking.rank(
            candidates = candidates,
            keywordHits = emptySet(),
            vectors = vectors,
            queryVector = listOf(1f, 0f),
        )

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `a flat field leaves keyword results exactly as they were`() {
        val candidates = listOf(event("a", "Kviz", startTime = 10), event("b", "Kviz 2", startTime = 20))
        val vectors = mapOf("a" to listOf(1f, 0.78f), "b" to listOf(1f, 0.79f))

        val ranked = SemanticRanking.rank(
            candidates = candidates,
            keywordHits = setOf("a", "b"),
            vectors = vectors,
            queryVector = listOf(1f, 0f),
        )

        assertEquals(listOf("a", "b"), ranked.map { it.id })
        assertTrue(ranked.all { it.relevance == null })
    }

    @Test
    fun `keyword hit stays even when its similarity is low`() {
        val ranked = SemanticRanking.rank(
            candidates = listOf(event("weak", "Kviz"), event("strong", "Drugo")),
            keywordHits = setOf("weak"),
            vectors = mapOf("weak" to listOf(0f, 1f), "strong" to listOf(1f, 0f)),
            queryVector = listOf(1f, 0f),
        )

        assertEquals(listOf("strong", "weak"), ranked.map { it.id })
        assertEquals(0.0, ranked.last().relevance!!, 1e-9)
    }

    @Test
    fun `results are ordered by similarity and capped`() {
        // Sest bliskih i dvadeset nevezanih: polje ima jasan vrh
        val near = (1..6).map { event("near$it", "Blizak $it") }
        val far = (1..20).map { event("far$it", "Nevezan $it") }
        val vectors = near.associate { it.id to listOf(1f, 0.01f * it.id.removePrefix("near").toFloat()) } +
            far.associate { it.id to listOf(0f, 1f) }

        val ranked = SemanticRanking.rank(
            candidates = near + far,
            keywordHits = emptySet(),
            vectors = vectors,
            queryVector = listOf(1f, 0f),
            maxRelated = 5,
        )

        assertEquals(5, ranked.size)
        assertEquals(listOf("near1", "near2", "near3", "near4", "near5"), ranked.map { it.id })
        assertTrue(ranked.zipWithNext().all { (first, second) -> first.relevance!! >= second.relevance!! })
    }
}
