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

import com.typesafe.config.{Config, ConfigFactory}
import java.nio.file.{FileSystem, FileSystems}
import scala.collection.JavaConverters._
import slick.driver.H2Driver.api.Database

package object config {

  private val defaults: Map[String, String] =
    Map(
      "file-store.path" -> "store",
      "server.hostname" -> "localhost",
      "server.port"     -> "8080",
      "upload.key"      -> "file")

  private val fs: FileSystem           = FileSystems.getDefault
  private val configFile: java.io.File = fs.getPath("hyponome.conf").toFile
  private val configDefault: Config    = ConfigFactory.parseMap(defaults.asJava)
  val config: Config                   = ConfigFactory.parseFile(configFile).withFallback(configDefault)

  val defaultConfig =
    ServiceConfig(
      { () => Database.forConfig("h2") },
      fs.getPath(config.getString("file-store.path")),
      config.getString("server.hostname"),
      config.getInt("server.port"),
      config.getString("upload.key"))
}
