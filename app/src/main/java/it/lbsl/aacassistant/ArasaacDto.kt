package it.lbsl.aacassistant

import com.google.gson.annotations.SerializedName

data class PictogramDto(
    @SerializedName("_id") val id: Int = 0,
    val keywords: List<KeywordDto> = emptyList())
data class KeywordDto( val keyword: String= "")