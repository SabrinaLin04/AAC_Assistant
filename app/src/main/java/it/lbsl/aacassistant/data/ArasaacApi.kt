package it.lbsl.aacassistant.data

import retrofit2.http.GET
import retrofit2.http.Path

interface ArasaacApi {
    @GET("pictograms/{lang}/bestsearch/{text}")
    suspend fun bestsearch(
        @Path("lang") lang : String,
        @Path("text") text : String
    ) : List<PictogramDto>

    @GET("pictograms/{lang}/search/{text}")
    suspend fun search(
        @Path("lang") lang : String,
        @Path("text") text : String
    ) : List<PictogramDto>
}