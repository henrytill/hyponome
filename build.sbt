import Dependencies._

val commonOptions = Seq(
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
  "-unchecked"
)

val consoleOptions = commonOptions diff Seq(
  "-Ywarn-unused-import"
)

lazy val commonSettings = Seq(
  name := "hyponome",
  organization := "net.xngns",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  scalacOptions := commonOptions,
  scalacOptions in (Compile, console) := consoleOptions,
  scalacOptions in (Test, console) := consoleOptions,
  fork in Test := true,
  initialCommands in console := """
    import hyponome._, actor._, core._, db._, file._, http._
    import java.nio.file._
    val fs: FileSystem  = FileSystems.getDefault()
  """,
  wartremoverErrors in (Compile, compile) ++= Warts.allBut(
    Wart.Any,
    Wart.DefaultArguments,
    Wart.Throw
  )
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= coreDeps)
