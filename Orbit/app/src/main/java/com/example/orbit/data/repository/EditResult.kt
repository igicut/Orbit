package com.example.orbit.data.repository

/** F-12: ishod izmene dogadjaja; izmena trazi mrezu, isto kao brisanje i otkazivanje */
sealed interface EditResult {
    data object Success : EditResult
    data object NoConnection : EditResult

    /** Server nije prihvatio izmenu, npr. dogadjaj je u medjuvremenu poceo ili je otkazan */
    data object Rejected : EditResult
}
