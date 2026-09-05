package com.example.kpkn.data.competitions

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PowerliftingFederation(
    val id: String,
    val name: String,
    val shortName: String,
    val country: String,
    val countryCode: String,
    val continent: String,
    val pointsFormula: String,
    val colorHex: String,
    val logoFile: String? = null,
)

@Serializable
private data class PowerliftingFederationCatalogFile(
    val federations: List<PowerliftingFederation>,
)

object PowerliftingFederationCatalog {
    const val CUSTOM_ID = "custom"
    const val ASSET_PATH = "competitions/powerlifting_federations.json"
    const val LOGO_ASSET_DIR = "competitions/logos"

    val continents = listOf(
        "WORLD" to "Mundial",
        "LATAM" to "Latam",
        "NORTH_AMERICA" to "Norteamérica",
        "EUROPE" to "Europa",
        "ASIA" to "Asia",
        "AFRICA" to "África",
        "OCEANIA" to "Oceanía",
    )

    private val json = Json { ignoreUnknownKeys = true }

    val all: List<PowerliftingFederation> = json.decodeFromString<PowerliftingFederationCatalogFile>(
        EMBEDDED_CATALOG_JSON,
    ).federations

    fun byId(id: String?): PowerliftingFederation? =
        id?.takeIf { it.isNotBlank() && it != CUSTOM_ID }?.let { wanted ->
            all.firstOrNull { it.id.equals(wanted, ignoreCase = true) }
        }

    fun search(query: String, continent: String? = null): List<PowerliftingFederation> {
        val needle = query.trim().lowercase()
        return all.filter { fed ->
            (continent.isNullOrBlank() || fed.continent == continent) &&
                (needle.isEmpty() ||
                    fed.name.lowercase().contains(needle) ||
                    fed.shortName.lowercase().contains(needle) ||
                    fed.country.lowercase().contains(needle) ||
                    fed.countryCode.lowercase().contains(needle))
        }
    }

    fun isCustom(id: String?): Boolean = id.isNullOrBlank() || id == CUSTOM_ID
}

private const val EMBEDDED_CATALOG_JSON = """
{
  "federations": [
    {"id":"ipf","name":"International Powerlifting Federation","shortName":"IPF","country":"Internacional","countryCode":"INTL","continent":"WORLD","pointsFormula":"IPF_GL","colorHex":"#1B4F9C","logoFile":"ipf.png"},
    {"id":"epf","name":"European Powerlifting Federation","shortName":"EPF","country":"Europa","countryCode":"EU","continent":"EUROPE","pointsFormula":"IPF_GL","colorHex":"#163A7A","logoFile":"epf.png"},
    {"id":"napf","name":"North American Powerlifting Federation","shortName":"NAPF","country":"Norteamérica","countryCode":"NA","continent":"NORTH_AMERICA","pointsFormula":"IPF_GL","colorHex":"#1E5A8A","logoFile":"napf.png"},
    {"id":"fesupo","name":"Federación Sudamericana de Powerlifting","shortName":"FESUPO","country":"Sudamérica","countryCode":"SA","continent":"LATAM","pointsFormula":"IPF_GL","colorHex":"#1A6B4A","logoFile":"fesupo.png"},
    {"id":"usapl","name":"USA Powerlifting","shortName":"USAPL","country":"Estados Unidos","countryCode":"US","continent":"NORTH_AMERICA","pointsFormula":"IPF_GL","colorHex":"#B22234","logoFile":"usapl.png"},
    {"id":"cpu","name":"Canadian Powerlifting Union","shortName":"CPU","country":"Canadá","countryCode":"CA","continent":"NORTH_AMERICA","pointsFormula":"IPF_GL","colorHex":"#D80621","logoFile":"cpu.png"},
    {"id":"british_powerlifting","name":"British Powerlifting","shortName":"BP","country":"Reino Unido","countryCode":"GB","continent":"EUROPE","pointsFormula":"IPF_GL","colorHex":"#012169","logoFile":"bp.png"},
    {"id":"bvdk","name":"Bundesverband Deutscher Kraftdreikämpfer","shortName":"BVDK","country":"Alemania","countryCode":"DE","continent":"EUROPE","pointsFormula":"IPF_GL","colorHex":"#000000","logoFile":"bvdk.png"},
    {"id":"ffforce","name":"FFForce","shortName":"FFF","country":"Francia","countryCode":"FR","continent":"EUROPE","pointsFormula":"IPF_GL","colorHex":"#002395","logoFile":"ffforce.png"},
    {"id":"fehme","name":"Federación Española de Halterofilia y Powerlifting","shortName":"FEHME","country":"España","countryCode":"ES","continent":"EUROPE","pointsFormula":"IPF_GL","colorHex":"#AA151B","logoFile":"fehme.png"},
    {"id":"fipl","name":"Federazione Italiana Powerlifting","shortName":"FIPL","country":"Italia","countryCode":"IT","continent":"EUROPE","pointsFormula":"IPF_GL","colorHex":"#009246","logoFile":"fipl.png"},
    {"id":"fechipo","name":"Federación Chilena de Powerlifting","shortName":"FECHIPO","country":"Chile","countryCode":"CL","continent":"LATAM","pointsFormula":"IPF_GL","colorHex":"#D52B1E","logoFile":"fechipo.png"},
    {"id":"fap","name":"Federación Argentina de Powerlifting","shortName":"FAP","country":"Argentina","countryCode":"AR","continent":"LATAM","pointsFormula":"IPF_GL","colorHex":"#74ACDF","logoFile":"fap.png"},
    {"id":"cbp","name":"Confederação Brasileira de Powerlifting","shortName":"CBP","country":"Brasil","countryCode":"BR","continent":"LATAM","pointsFormula":"IPF_GL","colorHex":"#009C3B","logoFile":"cbp.png"},
    {"id":"femepo","name":"Federación Mexicana de Powerlifting","shortName":"FEMEPO","country":"México","countryCode":"MX","continent":"LATAM","pointsFormula":"IPF_GL","colorHex":"#006847","logoFile":"femepo.png"},
    {"id":"fecolpot","name":"Federación Colombiana de Powerlifting","shortName":"FECOLPOT","country":"Colombia","countryCode":"CO","continent":"LATAM","pointsFormula":"IPF_GL","colorHex":"#FCD116","logoFile":"fecolpot.png"},
    {"id":"jpa","name":"Japan Powerlifting Association","shortName":"JPA","country":"Japón","countryCode":"JP","continent":"ASIA","pointsFormula":"IPF_GL","colorHex":"#BC002D","logoFile":"jpa.png"},
    {"id":"pau","name":"Powerlifting Australia","shortName":"PAU","country":"Australia","countryCode":"AU","continent":"OCEANIA","pointsFormula":"IPF_GL","colorHex":"#012169","logoFile":"pau.png"},
    {"id":"nzpf","name":"New Zealand Powerlifting Federation","shortName":"NZPF","country":"Nueva Zelanda","countryCode":"NZ","continent":"OCEANIA","pointsFormula":"IPF_GL","colorHex":"#00247D","logoFile":"nzpf.png"},
    {"id":"sapf","name":"South African Powerlifting Federation","shortName":"SAPF","country":"Sudáfrica","countryCode":"ZA","continent":"AFRICA","pointsFormula":"IPF_GL","colorHex":"#007A4D","logoFile":"sapf.png"},
    {"id":"wrpf","name":"World Raw Powerlifting Federation","shortName":"WRPF","country":"Internacional","countryCode":"INTL","continent":"WORLD","pointsFormula":"DOTS","colorHex":"#8B1E1E","logoFile":"wrpf.png"},
    {"id":"ipl","name":"International Powerlifting League","shortName":"IPL","country":"Internacional","countryCode":"INTL","continent":"WORLD","pointsFormula":"DOTS","colorHex":"#2C2C2C","logoFile":"ipl.png"},
    {"id":"gpc","name":"Global Powerlifting Committee","shortName":"GPC","country":"Internacional","countryCode":"INTL","continent":"WORLD","pointsFormula":"DOTS","colorHex":"#4A1C6B","logoFile":"gpc.png"},
    {"id":"wpc","name":"World Powerlifting Congress","shortName":"WPC","country":"Internacional","countryCode":"INTL","continent":"WORLD","pointsFormula":"WILKS","colorHex":"#0B3D2E","logoFile":"wpc.png"},
    {"id":"uspa","name":"United States Powerlifting Association","shortName":"USPA","country":"Estados Unidos","countryCode":"US","continent":"NORTH_AMERICA","pointsFormula":"DOTS","colorHex":"#1C3D73","logoFile":"uspa.png"},
    {"id":"gpa","name":"Global Powerlifting Alliance","shortName":"GPA","country":"Internacional","countryCode":"INTL","continent":"WORLD","pointsFormula":"WILKS","colorHex":"#5C1A1A","logoFile":"gpa.png"}
  ]
}
"""
