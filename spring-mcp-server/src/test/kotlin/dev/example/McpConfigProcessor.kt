package dev.example

import java.io.File

fun main() {
    val configProcessor = McpConfigProcessor()
    configProcessor.processConfig()
}
class McpConfigProcessor {

    fun processConfig() {
        try {
            // Read the mcp-config.json from classpath
            val configContent = readConfigFromClasspath()

            // Remove comments
            val withoutComments = removeComments(configContent)

            // Replace environment variables
            val withEnvVars = replaceEnvironmentVariables(withoutComments)

            // Save to Claude desktop config location
            saveToClaudeConfig(withEnvVars)

            println("Configuration processed successfully!")
            println("Output saved to: /Users/urs/Library/Application Support/Claude/claude_desktop_config.json")

        } catch (e: Exception) {
            println("Error processing configuration: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun readConfigFromClasspath(): String {
        val inputStream = this::class.java.classLoader.getResourceAsStream("mcp-config.json")
            ?: throw RuntimeException("mcp-config.json not found in classpath")

        return inputStream.bufferedReader().use { it.readText() }
    }

    fun removeComments(content: String): String {
        return content.lines()
            .filter { line -> !line.trim().startsWith("//") }
            .joinToString("\n")
    }

    fun replaceEnvironmentVariables(content: String): String {
        val envVarPattern = Regex("""\$\{env:([^}]+)}""")

        return envVarPattern.replace(content) { matchResult ->
            val envVarName = matchResult.groupValues[1]
            val envValue = System.getenv(envVarName)

            if (envValue != null) {
                println("Replacing ${matchResult.value} with environment variable value")
                envValue
            } else {
                println("Warning: Environment variable '$envVarName' not found, keeping placeholder")
                matchResult.value
            }
        }
    }

    private fun saveToClaudeConfig(content: String) {
        val configDir = File("/Users/urs/Library/Application Support/Claude")

        // Create directory if it doesn't exist
        if (!configDir.exists()) {
            configDir.mkdirs()
            println("Created directory: ${configDir.absolutePath}")
        }

        val configFile = File(configDir, "claude_desktop_config.json")
        configFile.writeText(content)

        println("Configuration saved to: ${configFile.absolutePath}")
    }
}
