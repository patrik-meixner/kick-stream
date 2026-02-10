package com.kickstream.data.api

import com.kickstream.data.api.model.TypesenseMultiSearchBody
import com.kickstream.data.api.model.TypesenseMultiSearchResults
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Kick channel search via Typesense at search.kick.com.
 * This is a separate service from the main Kick API — it uses a
 * public Typesense API key and returns fuzzy-matched results with
 * typo tolerance.
 */
interface KickSearchApi {

    @POST("multi_search")
    suspend fun multiSearch(
        @Body body: TypesenseMultiSearchBody,
    ): TypesenseMultiSearchResults
}
