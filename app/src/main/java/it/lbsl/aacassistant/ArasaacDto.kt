package it.lbsl.aacassistant

import com.google.gson.annotations.SerializedName

data class PictogramDto(
    @SerializedName("_id") val id: Int = 0,
    @SerializedName("keywords") val keywords: List<KeywordDto> = emptyList()
)

data class KeywordDto(
    @SerializedName("keyword") val keyword: String = "",
    @SerializedName("type") val type: Int = 0
)