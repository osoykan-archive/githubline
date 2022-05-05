import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

lazy val supportedScalaVersions = List("3.1.2", "2.13.8")
crossScalaVersions := supportedScalaVersions
name := "githubline"

version := "0.1"

scalaVersion := "3.1.2"
packageName in Docker := "githubline"
dockerBaseImage := "openjdk:19-jdk-alpine"
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "bash"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "fontconfig", "ttf-dejavu"))


enablePlugins(JavaAppPackaging)

val AkkaVersion = "2.6.19"
val AkkaHttpVersion = "10.2.9"
val http4sVersion = "0.21.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  ("com.typesafe.akka" %% "akka-http" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http-caching" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  "org.knowm.xchart" % "xchart" % "3.8.1" exclude("de.erichseifert.vectorgraphics2d", "VectorGraphics2D"),
  "com.github.nscala-time" %% "nscala-time" % "2.30.0",
  "de.erichseifert.vectorgraphics2d" % "VectorGraphics2D" % "0.13",
  "com.github.blemale" %% "scaffeine" % "5.1.2"
)
