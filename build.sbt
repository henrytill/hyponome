import Dependencies._

val commonOptions = Seq(
  "-Xfatal-warnings",
  "-Xfuture",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import",
  // "-Ywarn-value-discard",
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked"
)

val consoleOptions = commonOptions diff Seq(
  "-Ywarn-unused-import"
)

lazy val commonSettings = Seq(
  name := "hyponome",
  organization := "net.xngns",
  version := "0.1.0",
  scalaVersion := "2.11.7",
  scalacOptions := commonOptions,
  scalacOptions in (Compile, console) := consoleOptions,
  scalacOptions in (Test, console) := consoleOptions,
  fork in Test := true,
  initialCommands in console := """import hyponome._, db._, actor._""",
  wartremoverErrors in (Compile, compile) ++= Warts.allBut(Wart.Throw)
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= coreDeps)
