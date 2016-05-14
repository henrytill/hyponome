import Dependencies._

val commonOptions = Seq(
  "-language:higherKinds",
  "-Xfatal-warnings",
  "-Xfuture",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import",
  "-Ywarn-value-discard",
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked")

val consoleOptions = commonOptions diff Seq(
  "-Ywarn-unused-import")

lazy val up = taskKey[Unit]("Convenience task to run hyponome from sbt's interactive mode.")

lazy val initCmds = """
  import hyponome._, db._, file._, http._, util._
  import java.nio.file._
  val fs: FileSystem  = FileSystems.getDefault
"""

lazy val commonSettings = Seq(
  organization := "net.xngns",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  scalacOptions := commonOptions,
  scalacOptions in (Compile, console) := consoleOptions,
  scalacOptions in (Test, console) := consoleOptions,
  fork in Test := true,
  wartremoverErrors in (Compile, compile) ++= Warts.allBut(
    Wart.Any,
    Wart.DefaultArguments,
    Wart.ExplicitImplicitTypes,
    Wart.NonUnitStatements,
    Wart.Nothing,
    Wart.Null,
    Wart.Throw))

lazy val root = (project in file("."))
  .aggregate(core, http)
  .dependsOn(core, http)
  .settings(commonSettings: _*)
  .settings(initialCommands in console := initCmds)

lazy val core = (project in file("core"))
  .settings(name := "hyponome")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= commonDeps ++ coreDeps)

lazy val http = (project in file("http"))
  .settings(name := "hyponome-http",
            fullRunTask(up, Test, "hyponome.http.Main"),
            mainClass in (Compile, run) := Some("hyponome.http.Main"))
  .dependsOn(core % "test->test;compile->compile")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= commonDeps ++ httpDeps)
