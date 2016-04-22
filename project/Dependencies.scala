import sbt._

object Dependencies {

  val akkaVersion = "2.4.4"

  val coreDeps = Seq(
    "ch.qos.logback"         %  "logback-classic"                   % "1.1.3",
    "com.h2database"         %  "h2"                                % "1.4.190",
    "com.lihaoyi"            %% "pprint"                            % "0.3.8"     % "test",
    "com.typesafe"           %  "config"                            % "1.3.0",
    "com.typesafe.akka"      %% "akka-actor"                        % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-core"                    % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-experimental"            % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-spray-json-experimental" % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-xml-experimental"        % akkaVersion,
    "com.typesafe.akka"      %% "akka-stream"                       % akkaVersion,
    "com.typesafe.akka"      %% "akka-testkit"                      % akkaVersion % "test",
    "com.typesafe.slick"     %% "slick"                             % "3.1.1",
    "commons-codec"          %  "commons-codec"                     % "1.10",
    "org.scala-lang.modules" %% "scala-xml"                         % "1.0.5",
    "org.scalacheck"         %% "scalacheck"                        % "1.12.5"    % "test",
    "org.scalatest"          %% "scalatest"                         % "2.2.6"     % "test",
    "org.slf4j"              %  "slf4j-api"                         % "1.7.5"
  )
}
