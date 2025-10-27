package dev.example

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class MCPApp

fun main(args: Array<String>) {
    SpringApplication.run(MCPApp::class.java, *args)
}
