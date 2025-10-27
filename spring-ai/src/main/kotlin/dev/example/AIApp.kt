package dev.example

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class AIApp

fun main(args: Array<String>) {
    SpringApplication.run(AIApp::class.java, *args)
}
