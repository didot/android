/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.build.attribution.analyzers

import com.android.SdkConstants
import com.android.build.attribution.BuildAttributionManagerImpl
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Request.Companion.builder
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.io.FileUtil
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.file.Paths
import java.util.Base64


class DownloadsAnalyzerTest : AndroidGradleTestCase()  {

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  private lateinit var server1: HttpServerWrapper
  private lateinit var server2: HttpServerWrapper

  override fun setUp() {
    super.setUp()
    UsageTracker.setWriterForTest(tracker)
    StudioFlags.BUILD_ANALYZER_DOWNLOADS_ANALYSIS.override(true)

    // Set up servers.
    server1 = disposeOnTearDown(HttpServerWrapper("Server1"))
    server2 = disposeOnTearDown(HttpServerWrapper("Server2"))
  }

  override fun tearDown() {
    UsageTracker.cleanAfterTesting()
    StudioFlags.BUILD_ANALYZER_DOWNLOADS_ANALYSIS.clearOverride()
    super.tearDown()
  }

  fun testRunningBuildWithDownloadsFromLocalServers() {
    val gradleHome = Paths.get(myFixture.tempDirPath, "gradleHome").toString()

    invokeAndWaitIfNeeded {
      ApplicationManager.getApplication().runWriteAction {
        GradleSettings.getInstance(myFixture.project).serviceDirectoryPath = gradleHome
      }
    }

    //Add files to server2. Server1 will return errors (404 for everything and 403 for one for a change).
    val emptyJarBytes = Base64.getDecoder().decode("UEsFBgAAAAAAAAAAAAAAAAAAAAAAAA==")
    val aPomBytes = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
          xmlns="http://maven.apache.org/POM/4.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <modelVersion>4.0.0</modelVersion>
        <groupId>example</groupId>
        <artifactId>A</artifactId>
        <version>1.0</version>
        <dependencies>
          <dependency>
            <groupId>example</groupId>
            <artifactId>B</artifactId>
            <version>1.0</version>
            <scope>compile</scope>
          </dependency>
        </dependencies>
      </project>
    """.trimIndent().encodeToByteArray()
    val bPomBytes = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
          xmlns="http://maven.apache.org/POM/4.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <modelVersion>4.0.0</modelVersion>
        <groupId>example</groupId>
        <artifactId>B</artifactId>
        <version>1.0</version>
        <dependencies>
        </dependencies>
      </project>
    """.trimIndent().encodeToByteArray()
    server2.createFileContext(FileRequest(
      path = "/example/A/1.0/A-1.0.jar",
      mime = "application/java-archive",
      bytes = emptyJarBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/A/1.0/A-1.0.pom",
      mime = "application/xml",
      bytes = aPomBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/B/1.0/B-1.0.jar",
      mime = "application/java-archive",
      bytes = emptyJarBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/B/1.0/B-1.0.pom",
      mime = "application/xml",
      bytes = bPomBytes
    ))
    server1.createErrorContext("/example/B/1.0/", 403, "Forbidden")

    prepareProjectForImport(TestProjectPaths.SIMPLE_APPLICATION)
    FileUtils.join(projectFolderPath, "app", SdkConstants.FN_BUILD_GRADLE).let { file ->
      val newContent = file.readText()
        .plus("""
          repositories {
              maven {
                  url "${server1.url}"
                  allowInsecureProtocol = true
                  metadataSources() {
                      mavenPom()
                      artifact()
                  }
              }
              maven {
                  url "${server2.url}"
                  allowInsecureProtocol = true
                  metadataSources() {
                      mavenPom()
                      artifact()
                  }
              }
          }
          configurations {
              myExtraDependencies
          }
          dependencies {
              myExtraDependencies 'example:A:1.0'
          }
          tasks.register('myTestTask') {
              dependsOn configurations.myExtraDependencies
              doLast {
                  println "classpath = ${'$'}{configurations.myExtraDependencies.collect { File file -> file.name }}"
              }
          }
        """.trimIndent()
        )
      FileUtil.writeToFile(file, newContent)
    }
    importProject()
    prepareProjectForTest(project, null)

    // Clear any requests happened on sync.
    HttpServerWrapper.detectedHttpRequests.clear()

    //Run build WITHOUT --offline as we need to contact local server
    val buildResult = invokeGradle(project) { gradleInvoker ->
      gradleInvoker.executeTasks(builder(project, projectFolderPath, "myTestTask").build())
    }

    // Build expected to succeed, if not - fail the test
    buildResult.buildError?.let { throw it }

    // Verify interaction with server was as expected.
    // Sometimes requests can change the order so compare without order.
    Truth.assertThat(HttpServerWrapper.detectedHttpRequests).containsExactlyElementsIn("""
      Server1: GET on /example/A/1.0/A-1.0.pom - return error 404
      Server1: HEAD on /example/A/1.0/A-1.0.jar - return error 404
      Server2: GET on /example/A/1.0/A-1.0.pom - OK
      Server1: GET on /example/B/1.0/B-1.0.pom - return error 403
      Server2: GET on /example/B/1.0/B-1.0.pom - OK
      Server2: GET on /example/A/1.0/A-1.0.jar - OK
      Server2: GET on /example/B/1.0/B-1.0.jar - OK
    """.trimIndent().split("\n"))

    // Verify analyzer result.
    val result = (project.getService(
      BuildAttributionManager::class.java
    ) as BuildAttributionManagerImpl).analyzersProxy.getDownloadsAnalyzerResult()

    Truth.assertThat(result.analyzerActive).isTrue()
    val testRepositoryResult = result.repositoryResults.map { TestingRepositoryResult(it) }
    Truth.assertThat(testRepositoryResult).containsExactly(
      // Only one missed because HEAD request is not reported by gradle currently.
      TestingRepositoryResult(DownloadsAnalyzer.OtherRepository(server1.authority), 0, 1, 1),
      TestingRepositoryResult(DownloadsAnalyzer.OtherRepository(server2.authority), 4, 0, 0),
    )
  }

  //TODO (mlazeba): add test for a partially broken download
}

private data class TestingRepositoryResult(
  val repository: DownloadsAnalyzer.Repository,
  val successRequestsCount: Int,
  val failedRequestsCount: Int,
  val missedRequestsCount: Int,
) {
  constructor(realResult: DownloadsAnalyzer.RepositoryResult) : this(
    realResult.repository,
    realResult.successRequestsCount,
    realResult.failedRequestsCount,
    realResult.missedRequestsCount
  )
}

private class FileRequest(
  val path: String,
  val mime: String,
  val bytes: ByteArray
)

private class HttpServerWrapper(
  val name: String
) : Disposable {
  private val LOCALHOST = "127.0.0.1"

  val server: HttpServer = HttpServer.create()

  init {
    with(server) {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
    }
    // Make servers just fail on any not added explicitly file.
    createErrorContext("/", 404, "File not found")
  }

  val authority: String get() = "$LOCALHOST:${server.address.port}"
  val url: String get() = "http://$authority"

  companion object {
    val detectedHttpRequests = mutableListOf<String>()
  }

  fun createFileContext(descriptor: FileRequest) = server.createContext(descriptor.path) { he: HttpExchange ->
    he.responseHeaders["Content-Type"] = descriptor.mime
    when (he.requestMethod) {
      "HEAD" -> {
        recordRequest(he, "OK")
        he.sendResponseHeaders(200, -1)
      }
      "GET" -> {
        recordRequest(he, "OK")
        he.sendResponseHeaders(200, descriptor.bytes.size.toLong())
        he.responseBody.use { it.write(descriptor.bytes) }
      }
      else -> sendError(he, 501, "Unsupported HTTP method")
    }
  }

  fun createErrorContext(path: String, errorCode: Int, message: String) = server.createContext(path) { he: HttpExchange ->
    sendError(he, errorCode, message)
  }

  private fun sendError(he: HttpExchange, rCode: Int, description: String) {
    recordRequest(he, "return error $rCode")
    he.responseHeaders["Content-Type"] = "text/plain; charset=utf-8"
    when (he.requestMethod) {
      "HEAD" -> {
        he.sendResponseHeaders(rCode, -1)
      }
      "GET" -> {
        val message = "HTTP error $rCode: $description"
        val messageBytes = message.toByteArray(charset("UTF-8"))
        he.sendResponseHeaders(rCode, messageBytes.size.toLong())
        he.responseBody.use { it.write(messageBytes) }
      }
    }
  }

  private fun recordRequest(he: HttpExchange, suffix: String) {
    detectedHttpRequests.add("$name: ${he.requestMethod} on ${he.requestURI} - $suffix")
  }

  override fun dispose() {
    server.stop(0)
  }
}
