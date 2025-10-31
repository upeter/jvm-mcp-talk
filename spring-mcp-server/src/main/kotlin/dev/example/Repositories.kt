package dev.example

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.orEmpty
import kotlin.collections.toSet

data class ConferenceSessionSearchResult(
    val title: String,
    val startsAt: String,
    val endsAt: String,
    val room: String,
    val speakers: List<String>,
    val score: Double
)

@Service
class SessionSearchRepository(val vectorStore: VectorStore) {

    fun searchSessions(query: String): List<ConferenceSessionSearchResult> {
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
            }
    }
}


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
class SessionPreferenceRepository {
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