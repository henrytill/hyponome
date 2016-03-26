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

package hyponome.file

import hyponome.core._
import java.nio.file._
import scala.concurrent.{blocking, ExecutionContext, Future}
import scala.util.{Success, Try}

trait HyponomeFile {

  val storePath: Path

  def createStore()(implicit ec: ExecutionContext): Future[Path] =
    Future {
      blocking {
        Files.createDirectories(storePath)
      }
    }

  def getFilePath(h: SHA256Hash): Path = {
    val (dir, file) = h.value.splitAt(2)
    storePath.resolve(dir).resolve(file).toAbsolutePath
  }

  def existsInStore(p: Path)(implicit ec: ExecutionContext): Future[Boolean] =
    Future {
      blocking {
        Files.exists(p)
      }
    }

  def copyToStore(hash: SHA256Hash, source: Path)(implicit ec: ExecutionContext): Future[PostStatus] =
    Future {
      blocking {
        val destination: Path = getFilePath(hash)
        val parent: Path = Files.createDirectories(destination.getParent)
        Files.copy(source, destination)
      }
    }.map { (p: Path) =>
      Created
    }.recover {
      case e: java.nio.file.FileAlreadyExistsException => Exists
    }

  def deleteFromStore(hash: SHA256Hash)(implicit ec: ExecutionContext): Future[DeleteStatus] =
    Future {
      blocking {
        val p: Path = getFilePath(hash)
        Files.delete(p)
      }
    }.map { (_: Unit) =>
      Deleted
    }.recover {
      case e: java.nio.file.NoSuchFileException => NotFound
    }
}
