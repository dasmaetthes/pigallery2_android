package com.example.data

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

enum class ShareValidityUnit(val apiType: Int) {
    MINUTES(1),
    HOURS(2),
    DAYS(3),
    MONTHS(4),
    NONE(99)
}

data class ShareValidity(
    val amount: Int = 30,
    val unit: ShareValidityUnit = ShareValidityUnit.DAYS
)

data class ShareValidityRequest(
    val amount: Int,
    val type: Int
)

data class CreateSharingPayload(
    val includeSubfolders: Boolean? = null,
    val searchQuery: Map<String, Any?>? = null,
    val password: String? = null,
    val preventDownload: Boolean = false,
    val valid: ShareValidityRequest
)

data class CreateSharingRequestBody(
    val createSharing: CreateSharingPayload
)

/**
 * Retrofit service interface for PiGallery2 share-link REST endpoints
 * (creating directory/search share links, listing active links, and revoking links).
 */
interface ShareApiService {

    @POST
    fun postShare(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: CreateSharingRequestBody
    ): Call<ResponseBody>

    @PUT
    fun putShare(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: CreateSharingRequestBody
    ): Call<ResponseBody>

    @GET
    fun getShareLinks(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Call<ResponseBody>

    @DELETE
    fun deleteShareLink(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Call<ResponseBody>
}

object ShareRetrofitFactory {
    fun create(
        baseUrl: String,
        client: OkHttpClient,
        moshi: Moshi
    ): ShareApiService {
        val normalizedBase = PiGalleryApi.ensureHttpUrl(baseUrl).trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ShareApiService::class.java)
    }
}
