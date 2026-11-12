// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Minimal Central Publisher API client: bundle upload + status poll.
 * @see <a href="https://central.sonatype.org/publish/publish-portal-api/">Publish Portal API</a>
 */
final class Bb4CentralPortalClient {

    static final String DEFAULT_UPLOAD_URL = 'https://central.sonatype.com/api/v1/publisher/upload'
    static final String DEFAULT_STATUS_URL = 'https://central.sonatype.com/api/v1/publisher/status'

    private final String uploadUrl
    private final String statusUrl
    private final HttpClient httpClient

    Bb4CentralPortalClient() {
        this(DEFAULT_UPLOAD_URL, DEFAULT_STATUS_URL, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build())
    }

    Bb4CentralPortalClient(String uploadUrl, String statusUrl, HttpClient httpClient) {
        this.uploadUrl = uploadUrl
        this.statusUrl = statusUrl
        this.httpClient = httpClient
    }

    static String bearerToken(String username, String password) {
        def raw = "${username}:${password}"
        return Base64.encoder.encodeToString(raw.getBytes(StandardCharsets.UTF_8))
    }

    /**
     * Upload a deployment bundle. Returns the deployment id from a 201 response body.
     */
    String uploadBundle(Path bundleZip, String username, String password, String publishingType, String publicationName) {
        def boundary = '----bb4Central' + UUID.randomUUID().toString().replace('-', '')
        def body = buildMultipartBody(boundary, bundleZip)
        def query = new StringBuilder('publishingType=').append(URLEncoder.encode(publishingType, StandardCharsets.UTF_8))
        if (publicationName) {
            query.append('&name=').append(URLEncoder.encode(publicationName, StandardCharsets.UTF_8))
        }
        def uri = URI.create(uploadUrl + '?' + query)

        def request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(10))
            .header('Authorization', 'Bearer ' + bearerToken(username, password))
            .header('Content-Type', 'multipart/form-data; boundary=' + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        def response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 201) {
            throw new IllegalStateException(
                "Central Publisher upload failed HTTP ${response.statusCode()}: ${response.body()}"
            )
        }
        def deploymentId = response.body()?.trim()
        if (!deploymentId) {
            throw new IllegalStateException('Central Publisher upload returned empty deployment id')
        }
        return deploymentId
    }

    /**
     * Poll deployment status until terminal state. Returns the final deploymentState.
     */
    String waitForDeployment(String deploymentId, String username, String password,
                             Duration timeout, Duration pollInterval, Closure logger = null) {
        def deadline = System.nanoTime() + timeout.toNanos()
        while (true) {
            def state = fetchStatus(deploymentId, username, password)
            def deploymentState = state.deploymentState?.toString()
            if (logger) {
                logger.call("Central deployment ${deploymentId} state=${deploymentState}")
            }
            if (deploymentState in ['PUBLISHED', 'VALIDATED', 'FAILED']) {
                if (deploymentState == 'FAILED') {
                    throw new IllegalStateException(
                        "Central deployment ${deploymentId} FAILED: ${state.errors ?: state}"
                    )
                }
                return deploymentState
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(
                    "Timed out waiting for Central deployment ${deploymentId} (last state=${deploymentState})"
                )
            }
            Thread.sleep(pollInterval.toMillis())
        }
    }

    Map fetchStatus(String deploymentId, String username, String password) {
        def uri = URI.create(statusUrl + '?id=' + URLEncoder.encode(deploymentId, StandardCharsets.UTF_8))
        def request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(60))
            .header('Authorization', 'Bearer ' + bearerToken(username, password))
            .header('accept', 'application/json')
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        def response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                "Central Publisher status failed HTTP ${response.statusCode()}: ${response.body()}"
            )
        }
        return parseSimpleJson(response.body())
    }

    static byte[] buildMultipartBody(String boundary, Path bundleZip) {
        def filename = bundleZip.fileName.toString()
        def fileBytes = Files.readAllBytes(bundleZip)
        def baos = new ByteArrayOutputStream()
        def writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)
        writer.append('--').append(boundary).append('\r\n')
        writer.append('Content-Disposition: form-data; name="bundle"; filename="')
            .append(filename).append('"\r\n')
        writer.append('Content-Type: application/octet-stream\r\n\r\n')
        writer.flush()
        baos.write(fileBytes)
        writer.append('\r\n--').append(boundary).append('--\r\n')
        writer.flush()
        return baos.toByteArray()
    }

    /**
     * Tiny JSON object parser sufficient for Central status payloads (no nested arrays needed).
     * Prefers groovy.json when available; falls back to regex extraction of deploymentState/errors.
     */
    static Map parseSimpleJson(String json) {
        try {
            def slurper = Class.forName('groovy.json.JsonSlurper').getDeclaredConstructor().newInstance()
            return slurper.parseText(json) as Map
        } catch (ClassNotFoundException ignored) {
            def stateMatcher = (json =~ /"deploymentState"\s*:\s*"([^"]+)"/)
            def errorsMatcher = (json =~ /"errors"\s*:\s*(\{[^}]*\}|\[[^\]]*\]|"[^"]*")/)
            def map = [:]
            if (stateMatcher.find()) {
                map.deploymentState = stateMatcher.group(1)
            }
            if (errorsMatcher.find()) {
                map.errors = errorsMatcher.group(1)
            }
            return map
        }
    }
}
