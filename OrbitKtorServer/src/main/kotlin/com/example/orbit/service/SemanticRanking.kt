package com.example.orbit.service

import com.example.orbit.model.ExposedEvent
import kotlin.math.sqrt

/**
 * Gemini vraca slicnosti u uskom pojasu: nad seed podacima i besmislena rec dobija 0.58-0.63,
 * a pravi pogodak 0.74. Apsolutni prag zato propusta sve; signal je koliko najbolji odskace.
 *
 * Najbolji rezultat mora ovoliko da nadmasi prosek polja da bismo verovali da upit
 * uopste nesto razlikuje (izmereno nad seed podacima, vidi FEATURES.md F-32).
 */
const val MIN_LEAD = 0.05

/** Koliko ispod najboljeg rezultat jos ulazi u listu */
const val RELATIVE_MARGIN = 0.03

/** Koliko dogadjaja sme da udje samo na osnovu slicnosti */
const val MAX_RELATED = 10

/** F-32: rangiranje javnih dogadjaja po slicnosti sa upitom */
object SemanticRanking {

    /**
     * Pogoci po recima se nikad ne gube; srodni se dodaju samo kad upit stvarno razlikuje.
     * Kad je polje ravno, vraca pogotke po recima nepromenjene, bez ijednog skora.
     */
    fun rank(
        candidates: List<ExposedEvent>,
        keywordHits: Set<String>,
        vectors: Map<String, List<Float>>,
        queryVector: List<Float>,
        minLead: Double = MIN_LEAD,
        relativeMargin: Double = RELATIVE_MARGIN,
        maxRelated: Int = MAX_RELATED,
    ): List<ExposedEvent> {

        val scored = candidates.mapNotNull { event ->
            vectors[event.id]?.let { vector -> event to cosineSimilarity(vector, queryVector) }
        }
        val keywordMatches = candidates.filter { it.id in keywordHits }
        if (scored.isEmpty()) return keywordMatches

        val scores = scored.map { it.second }
        val best = scores.max()
        val average = scores.average()

        // Bez odskoka od proseka upit ne razlikuje nista; pretraga ostaje tacno ona od pre
        if (best - average < minLead) return keywordMatches

        val cutoff = best - relativeMargin
        val related = scored
            .filter { it.first.id !in keywordHits && it.second >= cutoff }
            .sortedByDescending { it.second }
            .take(maxRelated)

        val ranked = (scored.filter { it.first.id in keywordHits } + related)
            .sortedByDescending { it.second }
        val rankedIds = ranked.mapTo(HashSet()) { it.first.id }

        // Pogodak po recima bez vektora: jos nije obradjen ili je Gemini pao
        val withoutVector = candidates.filter { it.id in keywordHits && it.id !in rankedIds }

        return ranked.map { (event, score) -> event.copy(relevance = score) } + withoutVector
    }

    /** Skalarni proizvod podeljen proizvodom duzina; 0 za prazan ili neuporediv vektor */
    internal fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0

        var dotProduct = 0.0
        var squaredA = 0.0
        var squaredB = 0.0
        for (index in a.indices) {
            val left = a[index].toDouble()
            val right = b[index].toDouble()
            dotProduct += left * right
            squaredA += left * left
            squaredB += right * right
        }

        val magnitude = sqrt(squaredA) * sqrt(squaredB)
        return if (magnitude == 0.0) 0.0 else dotProduct / magnitude
    }
}
