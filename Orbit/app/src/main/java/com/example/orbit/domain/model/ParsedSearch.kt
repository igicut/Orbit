package com.example.orbit.domain.model

/**
 * F-43: ono sto je AI razumeo iz jedne recenice.
 * Vrednosti su imena enuma kao tekst jer dolaze sa servera; nepoznato ime se preskace,
 * pa server i aplikacija koji se razidu ne mogu da obore pretragu.
 */
data class ParsedSearch(
    val keywords: String = "",
    val category: String? = null,
    val radius: String? = null,
    val dateWindow: String? = null,
    val sort: String? = null,
    val price: String? = null,
) {

    /** Krece od praznih filtera: recenica opisuje celu nameru, ne dopunu starih filtera */
    fun toFilters(): EventFilters = EventFilters(
        query = keywords.trim(),
        category = EventCategory.entries.firstOrNull { it.name == category },
        radius = SearchRadius.entries.firstOrNull { it.name == radius } ?: EventFilters.DEFAULT_RADIUS,
        dateWindow = DateWindow.entries.firstOrNull { it.name == dateWindow } ?: DateWindow.ANY,
        sort = EventSort.entries.firstOrNull { it.name == sort } ?: EventSort.SOONEST,
        price = PriceLimit.entries.firstOrNull { it.name == price } ?: PriceLimit.ANY,
    )
}
