package com.example.orbit

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {

    @Test
    fun `root endpoint responds`() = testApplication {
        configure()
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }
}
