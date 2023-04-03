import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := "githubline"

version := "0.1"

scalaVersion := "3.2.2"
packageName in Docker := "githubline"
dockerBaseImage := "temurin:19-jre-alpine"
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "bash"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "fontconfig", "ttf-dejavu"))

enablePlugins(JavaAppPackaging)

val AkkaVersion = "2.8.0"
val AkkaHttpVersion = "10.5.0"

libraryDependencies ++= Seq(
  ("com.typesafe.akka" %% "akka-stream" % AkkaVersion).cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http-caching" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  ("org.knowm.xchart" % "xchart" % "3.8.3" exclude("de.erichseifert.vectorgraphics2d", "VectorGraphics2D")),
  "com.github.nscala-time" %% "nscala-time" % "2.32.0",
  "de.erichseifert.vectorgraphics2d" % "VectorGraphics2D" % "0.13",
  ("com.github.blemale" %% "scaffeine" % "5.2.1").cross(CrossVersion.for3Use2_13)
)
