// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import groovy.lang.Closure
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.UUID

/**
 * Minimal Central Publisher API client: bundle upload + status poll.
 * @see <a href="https://central.sonatype.org/publish/publish-portal-api/">Publish Portal API</a>
 */
class Bb4CentralPortalClient(
    private val uploadUrl: String = DEFAULT_UPLOAD_URL,
    private val statusUrl: String = DEFAULT_STATUS_URL,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build(),
) {

    companion object {
        const val DEFAULT_UPLOAD_URL = "https://central.sonatype.com/api/v1/publisher/upload"
        const val DEFAULT_STATUS_URL = "https://central.sonatype.com/api/v1/publisher/status"

        @JvmStatic
        fun bearerToken(username: String, password: String): String {
            val raw = "$username:$password"
            return Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        }

        @JvmStatic
        fun buildMultipartBody(boundary: String, bundleZip: Path): ByteArray {
            val filename = bundleZip.fileName.toString()
            val fileBytes = Files.readAllBytes(bundleZip)
            val baos = ByteArrayOutputStream()
            val writer = OutputStreamWriter(baos, StandardCharsets.UTF_8)
            writer.append("--").append(boundary).append("\r\n")
            writer.append("Content-Disposition: form-data; name=\"bundle\"; filename=\"")
                .append(filename).append("\"\r\n")
            writer.append("Content-Type: application/octet-stream\r\n\r\n")
            writer.flush()
            baos.write(fileBytes)
            writer.append("\r\n--").append(boundary).append("--\r\n")
            writer.flush()
            return baos.toByteArray()
        }

        /**
         * Tiny JSON object parser sufficient for Central status payloads.
         * Prefers groovy.json when available; falls back to regex extraction.
         */
        @JvmStatic
        fun parseSimpleJson(json: String): Map<String, Any?> {
            try {
                val slurperClass = Class.forName("groovy.json.JsonSlurper")
                val slurper = slurperClass.getDeclaredConstructor().newInstance()
                @Suppress("UNCHECKED_CAST")
                return slurperClass.getMethod("parseText", String::class.java)
                    .invoke(slurper, json) as Map<String, Any?>
            } catch (_: ClassNotFoundException) {
                val stateMatcher = Regex(""""deploymentState"\s*:\s*"([^"]+)"""").find(json)
                val errorsMatcher = Regex(""""errors"\s*:\s*(\{[^}]*\}|\[[^\]]*\]|"[^"]*")""").find(json)
                val map = LinkedHashMap<String, Any?>()
                if (stateMatcher != null) {
                    map["deploymentState"] = stateMatcher.groupValues[1]
                }
                if (errorsMatcher != null) {
                    map["errors"] = errorsMatcher.groupValues[1]
                }
                return map
            }
        }
    }

    /**
     * Upload a deployment bundle. Returns the deployment id from a 201 response body.
     */
    fun uploadBundle(
        bundleZip: Path,
        username: String,
        password: String,
        publishingType: String,
        publicationName: String?,
    ): String {
        val boundary = "----bb4Central" + UUID.randomUUID().toString().replace("-", "")
        val body = buildMultipartBody(boundary, bundleZip)
        val query = StringBuilder("publishingType=")
            .append(URLEncoder.encode(publishingType, StandardCharsets.UTF_8))
        if (!publicationName.isNullOrEmpty()) {
            query.append("&name=").append(URLEncoder.encode(publicationName, StandardCharsets.UTF_8))
        }
        val uri = URI.create("$uploadUrl?$query")

        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(10))
            .header("Authorization", "Bearer ${bearerToken(username, password)}")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 201) {
            throw IllegalStateException(
                "Central Publisher upload failed HTTP ${response.statusCode()}: ${response.body()}",
            )
        }
        val deploymentId = response.body()?.trim()
        if (deploymentId.isNullOrEmpty()) {
            throw IllegalStateException("Central Publisher upload returned empty deployment id")
        }
        return deploymentId
    }

    /**
     * Poll deployment status until terminal state. Returns the final deploymentState.
     */
    @JvmOverloads
    fun waitForDeployment(
        deploymentId: String,
        username: String,
        password: String,
        timeout: Duration,
        pollInterval: Duration,
        logger: Closure<*>? = null,
    ): String {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (true) {
            val state = fetchStatus(deploymentId, username, password)
            val deploymentState = state["deploymentState"]?.toString()
            logger?.call("Central deployment $deploymentId state=$deploymentState")
            if (deploymentState in listOf("PUBLISHED", "VALIDATED", "FAILED")) {
                if (deploymentState == "FAILED") {
                    throw IllegalStateException(
                        "Central deployment $deploymentId FAILED: ${state["errors"] ?: state}",
                    )
                }
                return deploymentState!!
            }
            if (System.nanoTime() >= deadline) {
                throw IllegalStateException(
                    "Timed out waiting for Central deployment $deploymentId (last state=$deploymentState)",
                )
            }
            Thread.sleep(pollInterval.toMillis())
        }
    }

    fun fetchStatus(deploymentId: String, username: String, password: String): Map<String, Any?> {
        val uri = URI.create(
            "$statusUrl?id=" + URLEncoder.encode(deploymentId, StandardCharsets.UTF_8),
        )
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer ${bearerToken(username, password)}")
            .header("accept", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException(
                "Central Publisher status failed HTTP ${response.statusCode()}: ${response.body()}",
            )
        }
        return parseSimpleJson(response.body())
    }
}
