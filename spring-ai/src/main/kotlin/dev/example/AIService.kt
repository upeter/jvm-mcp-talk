package dev.example

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema
import jakarta.annotation.PostConstruct
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springaicommunity.mcp.annotation.McpLogging
import org.springaicommunity.mcp.annotation.McpProgress
import org.springaicommunity.mcp.annotation.McpSampling
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

data class ConferenceSessionSearchResult(val title: String, val score: Double)

@Service
class ConferenceTools(
    val vectorStore: VectorStore,
    val conferencePreferenceRepository: SessionPreferenceRepository
) {
    @Tool(
        name = "conference-session-search",
        description = "Performs a similarity search for conference sessions and returns matching results with score."
    )
    fun searchSessions(
        @ToolParam(description = "The search query") query: String
    ): List<ConferenceSessionSearchResult> {
        val searchRequest = SearchRequest.builder()
            .query(query)
            .similarityThreshold(0.3)
            .topK(10).build().also { logger.info("Query: ${query}") }
        return vectorStore.similaritySearch(searchRequest)
            .groupBy { it.metadata.getValue("title") }
            .map { (title, documents) ->
                ConferenceSessionSearchResult(
                    title.toString(), documents.maxOf { it.score ?: 0.0 }
                )
            }
    }

    @Tool(
        name = "general-venue-information-jfall",
        description = "You provide general information aobut the Jall 2025 conference like location, address, ticket prices, hotels, dates, detailed session schedule, rooms etc."
    )
    fun getVenueInformation(): String = venueInformation


    @Tool(
        name = "get-preferred-sessions",
        description = "Get all preferred sessions of the user."
    )
    fun getPreferredSessionsBy(toolContext: ToolContext): Set<ConferenceSession> =
        conferencePreferenceRepository.getPreferredSessionsBy(toolContext.context.getValue("conversationId").toString()).also {
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
        conferencePreferenceRepository.addToPreferenceSessions(
            toolContext.context.getValue("conversationId").toString(), sessionTitle
        ).also { logger.info("Added session: $sessionTitle to preferences for conversationId: ${toolContext.context.getValue("conversationId")}") }
    }

    @Tool(
        name = "remove-preferred-sessions",
        description = "Remove sessions of preferences for the user."
    )
    fun removePreferredSession(
        @ToolParam(description = "the session title of the session to remove") sessionTitle: String,
        toolContext: ToolContext
    ) {
        conferencePreferenceRepository.removePreferredSession(
            toolContext.context.getValue("conversationId").toString(),
            sessionTitle
        ).also { logger.info("Removed session: $sessionTitle from preferences for conversationId: ${toolContext.context.getValue("conversationId")}") }
    }


    companion object {
        val venueInformation: String =
            ConferenceTools::class.java.getResourceAsStream("/data/dataset-jfall-venue.json").bufferedReader()
                .use {
                    it.readText()
                }
    }


}
@JsonIgnoreProperties(ignoreUnknown = true)
data class ConferenceSession(
    val title: String,
    val startsAt: String,
    val endsAt: String,
    val category: List<String>,
    val speakers: List<String>,
    val room: String
)


@Repository
class SessionPreferenceRepository() {

    fun getPreferredSessionsBy(conversationKey: String): Set<ConferenceSession> =
        preferences[conversationKey]?.toSet().orEmpty()

    fun addToPreferenceSessions(conversationKey: String, sessionTitle: String) {
        requireNotNull(findBySessionTitle(sessionTitle)).let { session ->
            updatePreferences(conversationKey) {
                it.add(session)
            }
        }
    }

    fun removePreferredSession(conversationKey: String, sessionTitle: String) {
        requireNotNull(findBySessionTitle(sessionTitle)).let { session ->
            updatePreferences(conversationKey) {
                it.remove(session)
            }
        }
    }

    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper()

        private val sessions: List<ConferenceSession> = run {
            val dataset: Dataset = mapper.readValue(
                SessionPreferenceRepository::class.java
                    .getResourceAsStream("/data/dataset-jfall-venue.json")
            )
            dataset.sessions
        }

        fun findBySessionTitle(sessionTitle: String): ConferenceSession? = sessions.firstOrNull {
            it.title.lowercase().startsWith(sessionTitle.lowercase())
        }


        fun updatePreferences(
            conversationId: String,
            transform: (PersistentSet<ConferenceSession>) -> PersistentSet<ConferenceSession>
        ) {
            preferences.compute(conversationId) { _, existing ->
                transform((existing ?: persistentSetOf()))
            }
        }


        private val preferences = ConcurrentHashMap<String, PersistentSet<ConferenceSession>>()


    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Dataset(val sessions: List<ConferenceSession> = emptyList())






