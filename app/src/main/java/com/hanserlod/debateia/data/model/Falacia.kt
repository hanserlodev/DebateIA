package com.hanserlod.debateia.data.model

import com.google.gson.annotations.SerializedName

/**
 * Configuración de falacias lógicas
 */
data class FalaciasConfig(
    @SerializedName("taxonomy_name")
    val taxonomyName: String,
    
    @SerializedName("version")
    val version: String,
    
    @SerializedName("falacias_list")
    val falaciasList: List<Falacia>
)

/**
 * Definición de una falacia lógica
 */
data class Falacia(
    @SerializedName("code")
    val code: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("keywords_detection")
    val keywordsDetection: List<String>
)
