package com.example.orbit

/**
 * Entry point.
 *
 * EngineMain reads src/main/resources/application.yaml, which lists the modules
 * to install. Adding a new module means adding it there as well as writing it.
 */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}
