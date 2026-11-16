package dev.pmtd.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MovieTvAgentApplication

fun main(args: Array<String>) {
    runApplication<MovieTvAgentApplication>(*args)
}
