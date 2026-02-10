package com.kickstream.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for Kick's Typesense search at search.kick.com/multi_search.
 *
 * Request is an array of { preset, q } objects.
 * Response is an array of result sets — one per preset — where each
 * result set contains hits[].document with the actual data.
 *
 * We only use the "channel_search" preset; category and tag presets
 * are included to match the expected request format but their
 * responses are ignored.
 */

@Serializable
data class TypesenseMultiSearchRequest(
    val preset: String,
    val q: String,
)

/** Wrapper for the multi_search endpoint — body must be {"searches": [...]} */
@Serializable
data class TypesenseMultiSearchBody(
    val searches: List<TypesenseMultiSearchRequest>,
)

/** Top-level response from multi_search — {"results": [...]} */
@Serializable
data class TypesenseMultiSearchResults(
    val results: List<TypesenseMultiSearchResponse> = emptyList(),
)

@Serializable
data class TypesenseMultiSearchResponse(
    val found: Int = 0,
    val hits: List<TypesenseHit> = emptyList(),
)

@Serializable
data class TypesenseHit(
    val document: TypesenseChannelDocument,
)

@Serializable
data class TypesenseChannelDocument(
    val id: String,
    val slug: String,
    val username: String = "",
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("followers_count") val followersCount: Int = 0,
    val verified: Boolean = false,
)
