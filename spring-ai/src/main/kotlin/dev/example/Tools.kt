package dev.example

import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

@Service
class ConferenceTools(
    val sessionSearchRepository: SessionSearchRepository
) {

    @Tool(
        name = "general-venue-information-jfall",
        description = "You provide general information aobut the Jall 2025 conference like location, address, ticket prices, hotels, dates, detailed session schedule, rooms etc."
    )
    fun getVenueInformation(): String = venueInformation


    @Tool(
        name = "conference-session-search",
        description = "Performs a similarity search for conference sessions and returns matching results with score."
    )
    fun searchSessions(
        @ToolParam(description = "The search query") query: String
    ): List<ConferenceSessionSearchResult> = sessionSearchRepository.searchSessions(query)


    companion object {
        val venueInformation: String =
            ConferenceTools::class.java.getResourceAsStream("/data/dataset-jfall-venue.json").bufferedReader()
                .use {
                    it.readText()
                }
    }


}