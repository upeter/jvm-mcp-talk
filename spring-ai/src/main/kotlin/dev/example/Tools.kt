package dev.example

import io.modelcontextprotocol.spec.McpSchema
import org.springaicommunity.mcp.annotation.McpLogging
import org.springaicommunity.mcp.annotation.McpProgress
import org.springaicommunity.mcp.annotation.McpSampling
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Service
class ConferenceTools(
    val sessionSearchRepository: SessionSearchRepository
) {

    @Tool(
        name = "general-venue-information-kdd",
        description = "You provide general information aobut the KotlinDevDay 2025 conference like location, address, ticket prices, hotels, dates, detailed session schedule, rooms etc."
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
            ConferenceTools::class.java.getResourceAsStream("/data/dataset-kdd-venue.json").bufferedReader()
                .use {
                    it.readText()
                }
    }


}

@Component
class McpClientHandlers(@Lazy private val chatClient: ChatClient)  // Lazy is needed to avoid circular dependency
{

    @McpProgress(clients = ["conference-advisor-server"])
    fun progressHandler(progressNotification: McpSchema.ProgressNotification) {
        logger.info(
            "MCP PROGRESS: [{}] progress: {} total: {} message: {}", progressNotification.progressToken(),
            progressNotification.progress(), progressNotification.total(), progressNotification.message()
        )
    }

    @McpLogging(clients = ["conference-advisor-server"])
    fun loggingHandler(loggingMessage: McpSchema.LoggingMessageNotification) {
        logger.info("MCP LOGGING: [{}] {}", loggingMessage.level(), loggingMessage.data())
    }

    @McpSampling(clients = ["conference-advisor-server"])
    fun samplingHandler(llmRequest: McpSchema.CreateMessageRequest): McpSchema.CreateMessageResult? {
        logger.info("MCP SAMPLING: {}", llmRequest)

        val llmResponse = chatClient.prompt()
            .system(llmRequest.systemPrompt())
            .user((llmRequest.messages().get(0).content() as McpSchema.TextContent).text())
            .call()
            .content()

        return McpSchema.CreateMessageResult.builder().content(McpSchema.TextContent(llmResponse)).build()
    }

}