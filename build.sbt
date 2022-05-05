import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := "githubline"

version := "0.1"

scalaVersion := "2.13.8"
packageName in Docker := "githubline"
dockerBaseImage := "openjdk:8-jre-alpine"
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "bash"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "fontconfig", "ttf-dejavu"))

enablePlugins(JavaAppPackaging)

val AkkaVersion = "2.6.19"
val AkkaHttpVersion = "10.2.9"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-caching" % AkkaHttpVersion,
  "org.knowm.xchart" % "xchart" % "3.8.1" exclude("de.erichseifert.vectorgraphics2d", "VectorGraphics2D"),
  "com.github.nscala-time" %% "nscala-time" % "2.30.0",
  "de.erichseifert.vectorgraphics2d" % "VectorGraphics2D" % "0.13",
  "com.github.blemale" %% "scaffeine" % "5.1.2"
)
