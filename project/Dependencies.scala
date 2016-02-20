import sbt._

object Dependencies {

  val akkaVersion = "2.4.2"

  val coreDeps = Seq(
    "com.h2database"         %  "h2"                                % "1.4.190",
    "com.typesafe.akka"      %% "akka-actor"                        % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-core"                    % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-experimental"            % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-spray-json-experimental" % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-xml-experimental"        % akkaVersion,
    "com.typesafe.akka"      %% "akka-testkit"                      % akkaVersion % "test",
    "com.typesafe.slick"     %% "slick"                             % "3.1.1",
    "commons-codec"          %  "commons-codec"                     % "1.10",
    "org.scala-lang.modules" %% "scala-xml"                         % "1.0.5",
    "org.scalatest"          %% "scalatest"                         % "2.2.6"     % "test",
    "org.slf4j"              %  "slf4j-nop"                         % "1.7.5"
  )
}
