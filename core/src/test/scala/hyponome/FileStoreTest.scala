/*
 * Copyright 2016-2017 Henry Till
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

import java.nio.file.{Files, Path}
import org.scalacheck.{Properties, Prop}
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Scalaz._
import scalaz.concurrent.Task
import org.log4s._

import hyponome.test._

object FileStoreProperties {

  private val logger = getLogger

  private def roundTrip(ps: List[Path])(implicit ec: ExecutionContext): Task[List[AddStatus]] =
    for {
      ctx <- freshTestContext()
      ls  <- Task.now(localStore)
      _   <- ls.init.run(ctx)
      as  <- ps.map((p: Path) => ls.addFile(p, None, testUser, None).run(ctx)).sequence
    } yield as

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def prop_roundTrip(implicit ec: ExecutionContext): Prop = Prop.forAll(genNEListOfNEByteArrays) { (bas: List[Array[Byte]]) =>
    (for {
      t  <- Task.now(Files.createTempDirectory("hyponome-test-uploads-"))
      hs <- Task.now(bas.map(FileHash.fromBytes))
      ps <- Task.now(hs.map((h: FileHash) => t.resolve(h.toString)))
      zs <- Task.now(bas.zip(ps))
      _  <- Task.now(zs.foreach((t: (Array[Byte], Path)) => Files.write(t._2, t._1)))
      _  <- Task.now(logger.debug(s"Round-tripping ${zs.length} files..."))
      as <- roundTrip(ps)
      rs <- Task.now(as.map(_.hash))
    } yield hs.sameElements(rs)).unsafePerformSync
  }
}

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object FileStoreTest extends Properties("FileStore") {
  property("roundTrip") = FileStoreProperties.prop_roundTrip
}
