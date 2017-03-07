lazy val argonautVersion     = "6.1a"
lazy val http4sVersion       = "0.14.1a"
lazy val scalazVersion       = "7.2.4"
lazy val scalazStreamVersion = "0.8.2a"

lazy val commonDeps = Seq(
  compilerPlugin("org.wartremover" %% "wartremover" % "1.2.1"),
  "ch.qos.logback"      % "logback-classic"     % "1.1.3",
  "commons-codec"       % "commons-codec"       % "1.10",
  "org.log4s"          %% "log4s"               % "1.3.4",
  "org.scalacheck"     %% "scalacheck"          % "1.12.5" % "test",
  "org.scalatest"      %% "scalatest"           % "2.2.6"  % "test",
  "org.scalaz"         %% "scalaz-core"         % scalazVersion,
  "org.scalaz"         %% "scalaz-concurrent"   % scalazVersion)

lazy val coreDeps = Seq(
  "com.h2database"      % "h2"                  % "1.4.190",
  "com.typesafe.slick" %% "slick"               % "3.1.1")

lazy val httpDeps = Seq(
  "com.typesafe"        % "config"              % "1.3.0",
  "io.argonaut"        %% "argonaut"            % argonautVersion,
  "org.http4s"         %% "http4s-argonaut"     % http4sVersion % "test",
  "org.http4s"         %% "http4s-dsl"          % http4sVersion,
  "org.http4s"         %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"         %% "http4s-blaze-client" % http4sVersion,
  "org.scalaz.stream"  %% "scalaz-stream"       % scalazStreamVersion)

lazy val commonOptions = Seq(
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
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked")

lazy val wartremoverOptions = List(
  "Any",
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
  fork in Test := true)

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

lazy val root = (project in file("."))
  .aggregate(core, http)
  .dependsOn(core, http)
  .settings(commonSettings: _*)
