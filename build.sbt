
name := "trainmapper"

version := "0.1"

scalaVersion := "2.12.6"

val stompaVersion = "0.1.0-SNAPSHOT"
val scalaLoggingVersion = "3.5.0"
val circeVersion = "0.9.3"
val http4sVersion = "0.18.12"

scalacOptions ++= Seq("-feature", "-deprecation", "-Ywarn-unused-import", "-Xfatal-warnings", "-language:higherKinds")

resolvers ++= Seq(
  "mygrid" at "http://www.mygrid.org.uk/maven/repository/"
)

libraryDependencies ++= Seq(
  "io.chiv" %% "stompa-fs2" % stompaVersion,
  "com.github.tototoshi" %% "scala-csv" % "1.3.5",
  "uk.org.mygrid.resources.jcoord" % "jcoord" % "1.0",
  "com.typesafe" % "config" % "1.3.3"
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-java8"
).map(_ % circeVersion)

libraryDependencies ++=Seq(
  "org.http4s" % "http4s-core_2.12",
  "org.http4s"     %% "http4s-blaze-server",
  "org.http4s"     %% "http4s-dsl"
).map(_ % http4sVersion)
