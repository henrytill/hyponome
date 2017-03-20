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

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.UUID

import slick.driver.SQLiteDriver.api._
import scala.util.{Success, Try}

package object test extends TestData {

  def uuid(): String = UUID.randomUUID.toString

  def testStoreDir(): Path =
    Files.createTempDirectory("hyponome-")

  def freshTestContext() = LocalStoreContext(
    dbDef = Database.forURL(url = "jdbc:sqlite:file::memory:?cache=shared", keepAliveConnection = true),
    storePath = testStoreDir()
  )

  /**
    * Makes a SimpleFileVisitor to delete files and their containing
    * directories.
    *
    * [[https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileVisitor.html]]
    */
  private def makeDeleteFileVisitor: SimpleFileVisitor[Path] =
    new SimpleFileVisitor[Path] {
      override def visitFile(p: Path, attrs: BasicFileAttributes): FileVisitResult = {
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
