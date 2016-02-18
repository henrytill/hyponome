import sbt._

object Dependencies {

  val akkaVersion = "2.4.1"

  val akkaHttpVersion = "2.0.3"

  val coreDeps = Seq(
    "com.h2database"         %  "h2"                          % "1.4.190",
    "com.typesafe.akka"      %% "akka-actor"                  % akkaVersion,
    "com.typesafe.akka"      %% "akka-http-core-experimental" % akkaHttpVersion,
    "com.typesafe.akka"      %% "akka-http-experimental"      % akkaHttpVersion,
    "com.typesafe.akka"      %% "akka-http-xml-experimental"  % akkaHttpVersion,
    "com.typesafe.akka"      %% "akka-testkit"                % akkaVersion     % "test",
    "com.typesafe.slick"     %% "slick"                       % "3.1.1",
    "org.slf4j"              %  "slf4j-nop"                   % "1.6.4",
    "org.scala-lang.modules" %% "scala-xml"                   % "1.0.5",
    "org.scalatest"          %% "scalatest"                   % "2.2.6"         % "test"
  )
}
