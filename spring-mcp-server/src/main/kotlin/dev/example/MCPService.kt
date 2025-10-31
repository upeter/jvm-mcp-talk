package dev.example

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.*
import org.springaicommunity.mcp.annotation.*
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap


// MCP counterparts for Conference tools from spring-ai module

data class ConferenceSessionSearchResult(val title: String, val startsAt: String, val endsAt: String, val room: String, val speakers: List<String>, val score: Double)
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
        exchange: McpSyncServerExchange,
        @McpProgressToken progressToken:String?,
        @McpToolParam(description = "The search query") query: String
    ): List<ConferenceSessionSearchResult> {
        exchange.info("Start searching sessions for: $query")
        progressToken?.let{
            exchange.progressNotification(
                ProgressNotification(
                    progressToken, 0.0, 1.0, "Start searching sessions for $query "
                )
            )
        }
        val searchRequest = SearchRequest.builder()
            .query(query)
            .similarityThreshold(0.3)
            .topK(10).build().also { logger.info("Session Query: ${query}") }
        return vectorStore.similaritySearch(searchRequest)
            .groupBy { it.metadata.getValue("title") }
            .map { (title, documents) ->
                documents.maxBy { it.score ?: 0.0 }.let {
                    ConferenceSessionSearchResult(
                        title = title.toString(),
                        startsAt = it.metadata.getValue("startsAtLocalDateTime").toString(),
                        endsAt = it.metadata.getValue("endsAtLocalDateTime").toString(),
                        room = it.metadata.getValue("room").toString(),
                        speakers = it.metadata.getValue("speakers").toString().split(",").toList(),
                        score = it.score ?: 0.0
                    )
                }
            }.also {
                exchange.info("Found ${it.size} sessions for: $query")

                progressToken?.let{
                    exchange.progressNotification(
                        ProgressNotification(
                            progressToken, 1.0, 1.0, "Done searching sessions for $query "
                        )
                    )
                }
            }
    }

    @McpTool(
        name = "get-preferred-sessions",
        description = "Get all preferred sessions of the user."
    )
    fun getPreferredSessionsBy(exchange: McpSyncServerExchange): Set<ConferenceSession> {
        val id = exchange.sessionId()
        return conferencePreferenceRepository.getPreferredSessionsBy(id).also { logger.info("Found ${it.size} preferred sessions for conversationId: $id") }
    }

    @McpTool(
        name = "add-preferred-sessions",
        description = "Add a session to preferences for the user"
    )
    fun addPreferenceSessions(@McpToolParam(description = "the session title of the session to add") sessionTitle: String, exchange: McpSyncServerExchange) {
        val id = exchange.sessionId()
        return conferencePreferenceRepository.addToPreferenceSessions( id, sessionTitle).also { logger.info("Added session: $sessionTitle to preferences for conversationId: $id")}
    }

    @McpTool(
        name = "remove-preferred-sessions",
        description = "Remove a session from preferences for the user."
    )
    fun removePreferredSession(
        @McpToolParam(description = "the session title of the session to remove") sessionTitle: String, exchange: McpSyncServerExchange
    ) {
        val id = exchange.sessionId()
        return conferencePreferenceRepository.removePreferredSession(id, sessionTitle).also { logger.info("Removed session: $sessionTitle from preferences for conversationId: $id")}
    }

    @McpPrompt(
        name = "jfall-advisor-prompt",
        description = "Returns the JFall assistant system prompt used by the chat server."
    )
    fun jfallAdvisorPrompt(): String = MCP_PROMPT.also { logger.info("Returning jfall prompt.") }

    // MCP Resource counterpart: expose the venue information as a retrievable blob
    @McpResource(
        name = "general-venue-information-jfall",
        description = "Returns general JFall 2025 venue information including address, dates, hotels, and the detailed session schedule in JSON form.",
        uri = "static://data/dataset-jfall-venue.json"
    )
    fun getVenueInformation(): String = venueInformation.also { logger.info("Returning venue information.") }


    fun McpSyncServerExchange.info(message: String) {
        loggingNotification(LoggingMessageNotification.builder().level(LoggingLevel.INFO).data(message).build())
    }

    companion object {
        private val MCP_PROMPT = """
        You are a helpful and knowledgeable assistant for the JFall 2025 conference.
    
        🎯 Your objective is to help the user:
        - Discover interesting sessions
        - Manage their personal session preferences
        - Provide accurate and relevant venue information
    
        🧰 You have access to several tools. Use them wisely:
    
        • Use `conference-session-search` 
          → When the user wants to explore sessions based on a topic, speaker, or interest. 
          → Example: "Find sessions about Kotlin", "Are there talks on machine learning?"
    
        • Use `get-preferred-sessions` 
          → When the user asks to view their current preferred sessions or saved talks. 
          → Example: "What are my favorite sessions?", "Show my preferences."
    
        • Use `add-preferred-sessions` 
          → When the user wants to add a session to their personal list. 
          → The user will typically mention a session title they like.
          → Example: "Add 'Jetpack Compose in Production' to my list"
    
        • Use `remove-preferred-sessions` 
          → When the user wants to remove a session from their preferences.
          → Example: "Remove the session about coroutines"
    
        • Use `general-venue-information-jfall` 
          → When the user asks about practical or logistical details about the event, such as location, time, hotels, or schedule.
    
        🤖 Response guidelines:
        - Use tools when needed to gather up-to-date or personalized information.
        - Keep answers short, friendly, and informative.
        - Don’t fabricate answers — prefer tool or resource calls when in doubt.
    
        Always focus on providing value to the user in the context of the JFall 2025 conference.
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
