package dev.example

import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MCPAppConfig {


    @Bean
    fun any(vectorStore: VectorStore):Any {
        return vectorStore
    }
}