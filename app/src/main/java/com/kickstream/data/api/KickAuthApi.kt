package com.kickstream.data.api

import com.kickstream.data.api.model.AuthTokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface KickAuthApi {

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeCodeForToken(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String,
    ): AuthTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
    ): AuthTokenResponse

    @FormUrlEncoded
    @POST("oauth/revoke")
    suspend fun revokeToken(
        @Field("token") token: String,
        @Field("token_hint_type") tokenHintType: String = "access_token",
    )
}
