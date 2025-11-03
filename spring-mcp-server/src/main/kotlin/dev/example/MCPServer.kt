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

    // MCP System prompt counterpart: the JFall conference advisor prompt






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
            ConferenceMcpServer::class.java.getResourceAsStream("/data/dataset-jfall-venue.json").bufferedReader()
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
