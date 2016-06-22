import sbt._

object Dependencies {

  lazy val argonautVersion = "6.1a"

  lazy val http4sVersion = "0.14.1a"

  lazy val scalazVersion = "7.2.4"

  lazy val scalazStreamVersion = "0.8.2a"

  lazy val commonDeps = Seq(
    "ch.qos.logback"      % "logback-classic"     % "1.1.3",
    "commons-codec"       % "commons-codec"       % "1.10",
    "org.scalacheck"     %% "scalacheck"          % "1.12.5" % "test",
    "org.scalatest"      %% "scalatest"           % "2.2.6"  % "test",
    "org.scalaz"         %% "scalaz-core"         % scalazVersion,
    "org.scalaz"         %% "scalaz-concurrent"   % scalazVersion,
    "org.slf4j"           % "slf4j-api"           % "1.7.5")

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
}
