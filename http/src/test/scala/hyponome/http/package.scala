/*
 * Copyright 2016 Henry Till
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hyponome

import java.io.InputStream
import java.nio.file._
import java.security.cert.{CertificateFactory, Certificate}
import java.security.{SecureRandom, KeyStore}
import javax.net.ssl.{SSLContext, KeyManagerFactory, TrustManagerFactory}
import org.http4s.HttpService
import org.http4s.server.SSLSupport.StoreInfo
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{Server, ServerBuilder, SSLSupport}
import scalaz.concurrent.Task
import hyponome.http.config.ServiceConfig

package object http {

  private val fs: FileSystem = FileSystems.getDefault

  private val keypath: String =
    getClass.getClassLoader.getResource("keystore.jks").getPath()

  private def builder: ServerBuilder with SSLSupport = BlazeBuilder

  def testServer(cfg: ServiceConfig, svc: HttpService): Task[Server] =
    builder.withSSL(StoreInfo(keypath, "password"), keyManagerPassword = "password")
      .mountService(svc)
      .bindHttp(cfg.port)
      .start

  private def resourceStream(resourceName: String): InputStream = {
    val is = getClass.getClassLoader.getResourceAsStream(resourceName)
    require(is ne null, s"Resource $resourceName not found")
    is
  }

  private def loadX509Certificate(resourceName: String): Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(resourceStream(resourceName))

  val clientContext: SSLContext = {
    val keystore: KeyStore                       = KeyStore.getInstance("JKS")
    val keyManagerFactory: KeyManagerFactory     = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    val context: SSLContext                      = SSLContext.getInstance("TLS")
    keystore.load(null, null)
    keystore.setCertificateEntry("ca", loadX509Certificate("hyponome.pem"))
    keyManagerFactory.init(keystore, "password".toCharArray)
    trustManagerFactory.init(keystore)
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }
}
