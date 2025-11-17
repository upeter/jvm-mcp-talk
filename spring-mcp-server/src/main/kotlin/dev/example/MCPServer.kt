package dev.example

import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.*
import org.springaicommunity.mcp.annotation.*
import org.springframework.stereotype.Service


@Service
class ConferenceMcpServer(
    private val sessionSearchRepository: SessionSearchRepository) {

    // MCP Resource counterpart: expose the venue information as a retrievable blob

    // MCP Tool counterpart: similarity search for conference sessions

    // MCP System prompt counterpart: the KotlinDevDay conference advisor prompt



    companion object {
        private val MCP_PROMPT = """
        You are a helpful and knowledgeable assistant for the KotlinDevDay 2025 conference.
    
        🎯 Your objective is to help the user:
        - Discover interesting sessions
        - Manage their personal session preferences
        - Provide accurate and relevant venue information
    
        🧰 You have access to several tools. Use them wisely:
    
        • Use `general-venue-information-kdd` 
          → When the user asks about practical or logistical details about the event, such as location, time, hotels, or schedule.
    
        • Use `conference-session-search` 
          → When the user wants to explore sessions based on a topic, speaker, or interest. 
          → Example: "Find sessions about Kotlin", "Are there talks on machine learning?"
      
        🤖 Response guidelines:
        - Use tools when needed to gather up-to-date or personalized information.
        - Keep answers short, friendly, and informative.
        - Don’t fabricate answers — prefer tool or resource calls when in doubt.
    
        Always focus on providing value to the user in the context of the KotlinDevDay 2025 conference.
""".trimIndent()

        val venueInformation: String =
            ConferenceMcpServer::class.java.getResourceAsStream("/data/dataset-kdd-venue.json").bufferedReader()
                .use {
                    it.readText()
                }
    }

}

