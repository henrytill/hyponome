import Dependencies._

lazy val commonOptions = Seq("-language:higherKinds",
                             "-Xfatal-warnings",
                             "-Xfuture",
                             "-Xlint",
                             "-Yno-adapted-args",
                             "-Ywarn-dead-code",
                             "-Ywarn-numeric-widen",
                             "-Ywarn-unused-import",
                             "-Ywarn-value-discard",
                             "-deprecation",
                             "-encoding",
                             "UTF-8",
                             "-feature",
                             "-unchecked")

lazy val wartremoverOptions =
  List("Any",
       "AsInstanceOf",
       "DefaultArguments",
       "EitherProjectionPartial",
       "Enumeration",
       "Equals",
       "ExplicitImplicitTypes",
       "FinalCaseClass",
       "FinalVal",
       "ImplicitConversion",
       "IsInstanceOf",
       "JavaConversions",
       "LeakingSealed",
       "MutableDataStructures",
       "NoNeedForMonad",
       "NonUnitStatements",
       "Nothing",
       "Null",
       "Option2Iterable",
       "Overloading",
       "Product",
       "Return",
       "Serializable",
       "StringPlusAny",
       "Throw",
       "ToString",
       "TraversableOps",
       "TryPartial",
       "Var",
       "While").map((s: String) => s"-P:wartremover:traverser:org.wartremover.warts.$s")

lazy val consoleOptions = commonOptions diff Seq("-Ywarn-unused-import")

lazy val up = taskKey[Unit]("Convenience task to run hyponome from sbt's interactive mode.")

lazy val commonSettings = Seq(
  organization := "net.xngns",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  resolvers ++= Seq(Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases")),
  scalacOptions := commonOptions ++ wartremoverOptions,
  scalacOptions in (Compile, console) := consoleOptions,
  scalacOptions in (Test, console) := consoleOptions,
  fork in Test := true,
  scalafmtConfig := Some(file(".scalafmt.conf")))

lazy val core = (project in file("core"))
  .settings(name := "hyponome-core")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= commonDeps ++ coreDeps)

lazy val http = (project in file("http"))
  .settings(name := "hyponome-http",
            fullRunTask(up, Test, "hyponome.http.Main"),
            mainClass in (Compile, run) := Some("hyponome.http.Main"))
  .dependsOn(core % "test->test;compile->compile")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= commonDeps ++ httpDeps)

lazy val root =
  (project in file(".")).aggregate(core, http).dependsOn(core, http).settings(commonSettings: _*)
