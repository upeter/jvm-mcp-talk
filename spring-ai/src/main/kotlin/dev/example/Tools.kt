package dev.example

import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

@Service
class ConferenceTools(
    val sessionSearchRepository: SessionSearchRepository,
    val sessionPreferenceRepository: SessionPreferenceRepository
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


    @Tool(
        name = "get-preferred-sessions",
        description = "Get all preferred sessions of the user."
    )
    fun getPreferredSessionsBy(toolContext: ToolContext): Set<ConferenceSession> =
        sessionPreferenceRepository.getPreferredSessionsBy(toolContext.context.getValue("conversationId").toString())
            .also {
                logger.info("Found ${it.size} preferred sessions for conversationId: ${toolContext.context.getValue("conversationId")}")
            }

    @Tool(
        name = "add-preferred-sessions",
        description = "Add sessions to preferences for the user"
    )
    fun addPreferenceSessions(
        @ToolParam(description = "the session title to of the session to add") sessionTitle: String,
        toolContext: ToolContext
    ) {
        sessionPreferenceRepository.addToPreferenceSessions(
            toolContext.context.getValue("conversationId").toString(), sessionTitle
        ).also {
            logger.info(
                "Added session: $sessionTitle to preferences for conversationId: ${
                    toolContext.context.getValue("conversationId")
                }"
            )
        }
    }

    @Tool(
        name = "remove-preferred-sessions",
        description = "Remove sessions of preferences for the user."
    )
    fun removePreferredSession(
        @ToolParam(description = "the session title of the session to remove") sessionTitle: String,
        toolContext: ToolContext
    ) {
        sessionPreferenceRepository.removePreferredSession(
            toolContext.context.getValue("conversationId").toString(),
            sessionTitle
        ).also {
            logger.info(
                "Removed session: $sessionTitle from preferences for conversationId: ${
                    toolContext.context.getValue("conversationId")
                }"
            )
        }
    }


    companion object {
        val venueInformation: String =
            ConferenceTools::class.java.getResourceAsStream("/data/dataset-jfall-venue.json").bufferedReader()
                .use {
                    it.readText()
                }
    }


}