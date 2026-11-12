// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import com.sun.net.httpserver.HttpServer

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class Bb4CentralPortalClientTest {

    @TempDir
    Path tempDir

    @Test
    void 'bearer token is base64 of user:password'() {
        def token = Bb4CentralPortalClient.bearerToken('user', 'pass')
        assertEquals(
            Base64.encoder.encodeToString('user:pass'.getBytes(StandardCharsets.UTF_8)),
            token,
        )
    }

    @Test
    void 'parseSimpleJson extracts deploymentState'() {
        def map = Bb4CentralPortalClient.parseSimpleJson('{"deploymentState":"PUBLISHED","errors":{}}')
        assertEquals('PUBLISHED', map.deploymentState)
    }

    @Test
    void 'multipart body contains bundle filename and bytes'() {
        def zip = tempDir.resolve('central-bundle.zip')
        Files.writeString(zip, 'bundle-bytes')
        def body = Bb4CentralPortalClient.buildMultipartBody('bound123', zip)
        def text = new String(body, StandardCharsets.UTF_8)
        assertTrue(text.contains('name="bundle"'))
        assertTrue(text.contains('filename="central-bundle.zip"'))
        assertTrue(text.contains('bundle-bytes'))
        assertTrue(text.contains('--bound123--'))
    }

    @Test
    void 'uploadBundle posts multipart and returns deployment id'() {
        def zip = tempDir.resolve('central-bundle.zip')
        Files.writeString(zip, 'payload')
        def capturedAuth = new AtomicReference<String>()
        def capturedQuery = new AtomicReference<String>()

        def server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/upload') { exchange ->
            capturedAuth.set(exchange.requestHeaders.getFirst('Authorization'))
            capturedQuery.set(exchange.requestURI.query)
            def response = 'dep-123'
            exchange.sendResponseHeaders(201, response.length())
            exchange.responseBody.withCloseable { it.write(response.bytes) }
        }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
        try {
            def port = server.address.port
            def client = new Bb4CentralPortalClient(
                "http://127.0.0.1:${port}/upload",
                "http://127.0.0.1:${port}/status",
                java.net.http.HttpClient.newHttpClient(),
            )
            def id = client.uploadBundle(zip, 'tokUser', 'tokPass', 'AUTOMATIC', 'g:a:1')
            assertEquals('dep-123', id)
            assertTrue(capturedAuth.get().startsWith('Bearer '))
            assertTrue(capturedQuery.get().contains('publishingType=AUTOMATIC'))
            assertTrue(capturedQuery.get().contains('name='))
        } finally {
            server.stop(0)
        }
    }

    @Test
    void 'zipDirectoryContents puts files at archive root'() {
        def src = tempDir.resolve('staging').toFile()
        def nested = new File(src, 'com/example/lib/1.0.0')
        nested.mkdirs()
        new File(nested, 'lib-1.0.0.pom').text = '<project/>'
        def out = tempDir.resolve('out.zip').toFile()

        Bb4CentralBundleSupport.zipDirectoryContents(src, out)

        def names = []
        new java.util.zip.ZipFile(out).withCloseable { zf ->
            zf.entries().each { names << it.name }
        }
        assertTrue(names.contains('com/example/lib/1.0.0/lib-1.0.0.pom'))
    }
}
