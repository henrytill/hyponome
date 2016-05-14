import sbt._

object Dependencies {

  val http4sVersion = "0.14.0a-SNAPSHOT"

  val scalazVersion = "7.2.2"

  val commonDeps = Seq(
    "ch.qos.logback"      % "logback-classic"     % "1.1.3",
    "commons-codec"       % "commons-codec"       % "1.10",
    "org.scalacheck"     %% "scalacheck"          % "1.12.5" % "test",
    "org.scalatest"      %% "scalatest"           % "2.2.6"  % "test",
    "org.scalaz"         %% "scalaz-core"         % scalazVersion,
    "org.scalaz"         %% "scalaz-concurrent"   % scalazVersion,
    "org.slf4j"           % "slf4j-api"           % "1.7.5")

  val coreDeps = Seq(
    "com.h2database"      % "h2"                  % "1.4.190",
    "com.typesafe.slick" %% "slick"               % "3.1.1")

  val httpDeps = Seq(
    "com.typesafe"        % "config"              % "1.3.0",
    "io.argonaut"        %% "argonaut"            % "6.2-SNAPSHOT" changing(),
    "io.argonaut"        %% "argonaut-scalaz"     % "6.2-SNAPSHOT" changing(),
    "io.argonaut"        %% "argonaut-monocle"    % "6.2-SNAPSHOT" changing(),
    "org.http4s"         %% "http4s-argonaut"     % http4sVersion % "test",
    "org.http4s"         %% "http4s-dsl"          % http4sVersion,
    "org.http4s"         %% "http4s-blaze-server" % http4sVersion,
    "org.http4s"         %% "http4s-blaze-client" % http4sVersion,
    "org.scalaz.stream"  %% "scalaz-stream"       % "0.8a")
}
