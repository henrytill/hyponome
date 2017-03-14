lazy val scalazVersion       = "7.2.4"

lazy val commonDepsSettings = Seq(
  libraryDependencies ++= Seq(
    compilerPlugin("org.wartremover" %% "wartremover" % "1.2.1"),
    "ch.qos.logback"      % "logback-classic"     % "1.1.3",
    "com.novocode"        % "junit-interface"     % "0.11"   % "test",
    "org.log4s"          %% "log4s"               % "1.3.4",
    "org.scalacheck"     %% "scalacheck"          % "1.12.5" % "test",
    "org.scalaz"         %% "scalaz-core"         % scalazVersion,
    "org.scalaz"         %% "scalaz-concurrent"   % scalazVersion))

lazy val coreDepsSettings = Seq(
  libraryDependencies ++= Seq(
    "com.h2database"      % "h2"                  % "1.4.190",
    "com.typesafe.slick" %% "slick"               % "3.1.1",
    "net.xngns"          %% "klados-hash"         % "0.1.0-37e80e3"))

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

lazy val initialConsoleCommands =
  """|import hyponome._
     |import java.nio.file.FileSystems
     |import scala.concurrent.ExecutionContext.Implicits.global
     |val fs = FileSystems.getDefault
     |""".stripMargin

lazy val initialTestConsoleCommands =
  initialConsoleCommands ++ "import hyponome.test._\n"

lazy val commonSettings =
  commonDepsSettings ++
  Seq(organization := "net.xngns",
      version := "0.1.0-SNAPSHOT",
      scalaVersion := "2.11.8",
      scalacOptions ++= commonOptions ++ wartremoverOptions,
      scalacOptions in console in Compile --= wartremoverOptions ++ Seq("-Ywarn-unused-import"),
      scalacOptions in console in Test    --= wartremoverOptions ++ Seq("-Ywarn-unused-import"),
      initialCommands in console in Compile := initialConsoleCommands,
      initialCommands in console in Test    := initialTestConsoleCommands,
      resolvers ++= Seq(
        Resolver.sonatypeRepo("snapshots"),
        Resolver.sonatypeRepo("releases"),
        Resolver.bintrayRepo("xngns", "maven")),
      testOptions += Tests.Argument(TestFrameworks.JUnit, "-v"),
      fork in Test := true)

lazy val core = (project in file("core"))
  .settings(name := "hyponome-core")
  .settings(commonSettings: _*)
  .settings(coreDepsSettings: _*)

lazy val root = (project in file("."))
  .aggregate(core)
  .dependsOn(core % "test->test;compile->compile")
  .settings(commonSettings: _*)
