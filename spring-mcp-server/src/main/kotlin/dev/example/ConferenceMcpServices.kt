package dev.example

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.springaicommunity.mcp.annotation.McpPrompt
import org.springaicommunity.mcp.annotation.McpResource
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.ai.support.ToolCallbacks
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2

// MCP counterparts for Conference tools from spring-ai module

data class ConferenceSessionSearchResult(val title: String, val score: Double)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConferenceSession(
    val title: String,
    val startsAt: String,
    val endsAt: String,
    val category: List<String> = emptyList(),
    val speakers: List<String> = emptyList(),
    val room: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Dataset(val sessions: List<ConferenceSession> = emptyList())

@Service
class ConferencePreferenceRepository {
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

    private fun findBySessionTitle(sessionTitle: String): ConferenceSession? = sessions.firstOrNull {
        it.title.lowercase().startsWith(sessionTitle.lowercase())
    }

    private fun updatePreferences(
        conversationId: String,
        transform: (MutableSet<ConferenceSession>) -> Unit
    ) {
        preferences.compute(conversationId) { _, existing ->
            val current = existing ?: mutableSetOf()
            transform(current)
            current
        }
    }

    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper()
        internal val sessions: List<ConferenceSession> = run {
            val dataset: Dataset = mapper.readValue(
                ClassPathResource("data/dataset-jfall-venue.json").inputStream
            )
            dataset.sessions
        }
        private val preferences = ConcurrentHashMap<String, MutableSet<ConferenceSession>>()
    }
}

@Service
class ConferenceMcpService(
    val vectorStore: VectorStore,
    private val conferencePreferenceRepository: ConferencePreferenceRepository,
) {

    // Simple similarity search over titles based on containment and Jaccard score of words
    @McpTool(
        name = "conference-session-search",
        description = "Performs a simple similarity search for conference sessions (title based) and returns matching results with a heuristic score."
    )
    fun searchSessions(
        @McpToolParam(description = "The search query") query: String
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

    @McpTool(
        name = "get-preferred-sessions",
        description = "Get all preferred sessions of the user."
    )
    fun getPreferredSessionsBy(
        @McpToolParam(description = "The conversation id to scope user preferences") conversationId: String
    ): Set<ConferenceSession> = conferencePreferenceRepository.getPreferredSessionsBy(conversationId)

    @McpTool(
        name = "add-preferred-sessions",
        description = "Add a session to preferences for the user"
    )
    fun addPreferenceSessions(
        @McpToolParam(description = "the session title of the session to add") sessionTitle: String,
        @McpToolParam(description = "The conversation id to scope user preferences") conversationId: String
    ) {
        conferencePreferenceRepository.addToPreferenceSessions(conversationId, sessionTitle)
    }

    @McpTool(
        name = "remove-preferred-sessions",
        description = "Remove a session from preferences for the user."
    )
    fun removePreferredSession(
        @McpToolParam(description = "the session title of the session to remove") sessionTitle: String,
        @McpToolParam(description = "The conversation id to scope user preferences") conversationId: String
    ) {
        conferencePreferenceRepository.removePreferredSession(conversationId, sessionTitle)
    }

    @McpPrompt(
        name = "jfall-system-prompt",
        description = "Returns the JFall assistant system prompt used by the chat server."
    )
    fun jfallSystemPrompt(): String = SYSTEM_PROMPT

    // MCP Resource counterpart: expose the venue information as a retrievable blob
    @McpResource(
        name = "general-venue-information-jfall",
        description = "Returns general JFall 2025 venue information including address, dates, hotels, and the detailed session schedule in JSON form.",
        uri = "file:///data/dataset-jfall-venue.json"
    )
    fun getVenueInformation(): String = venueInformation

    companion object {
        // Copied from AIController.SYSTEM_PROMPT to avoid cross-module dependency
        private val SYSTEM_PROMPT = """
            You are a helper assistant for the JFall 2025 conference. 
            Respond in a friendly, helpful manner.
            Objective: Assist the user in finding the best matching sessions for his preferences and provide relevant information about the conference.
            Make use of tools to fetch relevant information about sessions, speakers, and venue details.
        """.trimIndent()

        val venueInformation: String =
            ConferenceMcpService::class.java.getResourceAsStream("/data/dataset-jfall-venue.json").bufferedReader()
                .use {
                    it.readText()
                }
    }

}

//@Configuration
//class ConferenceMcpConfig(
//    private val conferenceMcpService: ConferenceMcpService,
//) {
//
//    // Expose all @McpTool methods as MCP tools
//    @Bean
//    fun conferenceToolCallbacks(): List<org.springframework.ai.tool.ToolCallback> =
//        ToolCallbacks.from(conferenceMcpService).toList()
//}
