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

import java.net.InetAddress
import java.nio.file._
import java.util.UUID.randomUUID
import java.util.concurrent.atomic.AtomicLong
import scala.util.{Success, Try}
import slick.driver.H2Driver.api._
import slick.driver.H2Driver.backend.DatabaseDef

package object test {

  // val hostname: String = InetAddress.getLocalHost().getHostName()
  val testHostname: String = "localhost"

  val testPort: Int = 4000

  val fs: FileSystem = FileSystems.getDefault

  val testStorePath: Path = fs.getPath("/tmp/hyponome/store")

  val testPDF: Path = {
    val s: String = getClass.getResource("/test.pdf").getPath
    fs.getPath(s)
  }

  val testPDFHash = SHA256Hash(
    "eba205fb9114750b2ce83db62f9c2a15dd068bcba31a2de32d8df7f7c8d85441")

  val ip: Option[InetAddress] = Some(InetAddress.getByName("192.168.1.253"))

  val add = Add(
    testHostname,
    testPort,
    testPDF,
    testPDFHash,
    Some(testPDF.toFile.getName),
    "application/octet-stream",
    testPDF.toFile.length,
    ip)

  val added = AddResponse(
    Added,
    getURI(add.hostname, add.port, add.hash, add.name),
    add.hash,
    add.name,
    add.contentType,
    add.length)

  val existed = AddResponse(
    Exists,
    getURI(add.hostname, add.port, add.hash, add.name),
    add.hash,
    add.name,
    add.contentType,
    add.length)

  val remove = Remove(add.hash, add.remoteAddress)

  val expected = File(add.hash, add.name, add.contentType, add.length)

  def makeCounter(): AtomicLong = new AtomicLong()

  def makeDbName(): String = randomUUID.toString()

  def makeTestDB: Function0[DatabaseDef] = { () =>
    Database.forURL(
      url = s"jdbc:h2:mem:${makeDbName()};CIPHER=AES",
      user = "hyponome",
      password = "hyponome hyponome", // password = "filepwd userpwd"
      driver = "org.h2.Driver",
      keepAliveConnection = true)
  }

  def makePersistentDBConfig(location: Path): Function0[DatabaseDef] = { () =>
    val p: String = location.toString
    Database.forURL(
      url = s"jdbc:h2:$p;CIPHER=AES",
      user = "hyponome",
      password = "hyponome hyponome", // password = "filepwd userpwd"
      driver = "org.h2.Driver",
      keepAliveConnection = true)
  }

  /**
    * Makes a SimpleFileVisitor to delete files and their containing
    * directories.
    *
    * [[https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileVisitor.html]]
    */
  private def makeDeleteFileVisitor: SimpleFileVisitor[Path] =
    new SimpleFileVisitor[Path] {
      override def visitFile(p: Path, attrs: attribute.BasicFileAttributes): FileVisitResult = {
        Files.delete(p)
        FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(p: Path, e: java.io.IOException): FileVisitResult = {
        Files.delete(p)
        FileVisitResult.CONTINUE
      }
    }

  private def recursiveDeletePath(p: Path): Path =
    Files.walkFileTree(p, makeDeleteFileVisitor)

  def deleteFolder(p: Path): Try[Path] =
    if (Files.exists(p)) Try(recursiveDeletePath(p))
    else Success(p)
}
