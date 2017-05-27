import com.typesafe.sbt.SbtGit.GitKeys._
import sbtprotobuf.ProtobufPlugin

lazy val circeVersion     = "0.7.1"
lazy val shapelessVersion = "2.3.2"

lazy val commonDepsSettings = Seq(
  libraryDependencies ++= Seq(
    compilerPlugin("org.wartremover" %% "wartremover" % "1.2.1"),
    "ch.qos.logback"  % "logback-classic" % "1.1.3",
    "co.fs2"         %% "fs2-core"        % "0.9.5",
    "co.fs2"         %% "fs2-cats"        % "0.3.0",
    "com.novocode"    % "junit-interface" % "0.11"   % "test",
    "org.log4s"      %% "log4s"           % "1.3.4",
    "org.scalacheck" %% "scalacheck"      % "1.13.4" % "test",
    "org.typelevel"  %% "cats"            % "0.9.0"))

lazy val coreDepsSettings = Seq(
  libraryDependencies ++= Seq(
    "com.typesafe.slick" %% "slick"       % "3.2.0",
    "net.xngns"          %% "klados-hash" % "0.1.0-37e80e3",
    "org.xerial"          % "sqlite-jdbc" % "3.16.1"))

lazy val jsonDepsSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless"            % shapelessVersion,
    "io.circe"    %% "circe-core"           % circeVersion,
    "io.circe"    %% "circe-generic"        % circeVersion,
    "io.circe"    %% "circe-generic-extras" % circeVersion,
    "io.circe"    %% "circe-parser"         % circeVersion))

lazy val commonOptions = Seq(
  "-language:higherKinds",
  "-language:implicitConversions",
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

lazy val wartremoverOptions = List(
  // "Any",
  // "AsInstanceOf",
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
  // "Nothing",
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

lazy val nonConsoleOptions =
  wartremoverOptions ++ Seq("-Ywarn-unused-import", -"Xfatal-warnings")

lazy val initialConsoleCommands =
  """|import hyponome._
     |import java.nio.file.FileSystems
     |import scala.concurrent.ExecutionContext.Implicits.global
     |import fs2.Strategy
     |val fs = FileSystems.getDefault
     |implicit val strat: Strategy = Strategy.fromFixedDaemonPool(8, threadName = "worker")
     |""".stripMargin

lazy val initialTestConsoleCommands =
  initialConsoleCommands ++ "import hyponome.test._\n"

lazy val commonSettings =
  commonDepsSettings ++
  Seq(organization := "net.xngns",
      version := "0.1.0-SNAPSHOT",
      scalaVersion := "2.12.1",
      crossScalaVersions := Seq("2.11.11", scalaVersion.value),
      scalacOptions ++= commonOptions ++ wartremoverOptions,
      scalacOptions in (Compile, console) ~= (_ filterNot (nonConsoleOptions.contains(_))),
      scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,
      initialCommands in (Compile, console) := initialConsoleCommands,
      initialCommands in (Test, console) := initialTestConsoleCommands,
      resolvers ++= Seq(
        Resolver.sonatypeRepo("snapshots"),
        Resolver.sonatypeRepo("releases"),
        Resolver.bintrayRepo("xngns", "maven")),
      testOptions += Tests.Argument(TestFrameworks.JUnit, "-v"),
      parallelExecution in Test := false,
      fork in Test := true)

lazy val core = (project in file("core"))
  .settings(name := "hyponome-core")
  .settings(commonSettings: _*)
  .settings(coreDepsSettings: _*)

lazy val json = (project in file("json"))
  .settings(name := "hyponome-json")
  .settings(commonSettings: _*)
  .settings(jsonDepsSettings: _*)
  .dependsOn(core % "test->test;compile->compile")

lazy val protobuf = (project in file("protobuf"))
  .settings(ProtobufPlugin.protobufSettings: _*)
  .settings(commonSettings: _*)
  .settings(name := "hyponome-protobuf",
            javaSource in ProtobufPlugin.protobufConfig := (sourceManaged in Compile).value)
  .dependsOn(core % "test->test;compile->compile")

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .settings(
    siteSubdirName in ScalaUnidoc := "latest/api",
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc),
    scmInfo := Some(ScmInfo(url("https://github.com/henrytill/hyponome"), "git@github.com:henrytill/hyponome.git")),
    git.remoteRepo := scmInfo.value.get.connection)
  .aggregate(core, json, protobuf)
  .dependsOn(core     % "test->test;compile->compile",
             json     % "test->test;compile->compile",
             protobuf % "test->test;compile->compile")
