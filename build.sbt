import sbt.Keys.libraryDependencies
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtcrossproject.CrossType

val stompaVersion = "0.1.0-SNAPSHOT"
val scalaLoggingVersion = "3.5.0"
val circeVersion = "0.9.3"
val http4sVersion = "0.18.12"
val scalaJsDomV = "0.9.6"
val scalaTagsV = "0.6.7"

/*
Scala JS setup below based on example provided at:
https://github.com/davenport-scala/http4s-scalajsexample
 */

lazy val commonSettings = {
  version := "0.1"
  scalaVersion := "2.12.6"
}

def includeInTrigger(f: java.io.File): Boolean =
  f.isFile && {
    val name = f.getName.toLowerCase
    name.endsWith(".scala") || name.endsWith(".js")
  }

lazy val shared =
  crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure).in(file("shared"))
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core",
        "io.circe" %%% "circe-generic",
        "io.circe" %%% "circe-parser",
        "io.circe" %%% "circe-java8"
      ).map(_ % circeVersion) ++ Seq(
        "com.lihaoyi" %%% "scalatags" % scalaTagsV
      )
    )


lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js


lazy val backend = (project in file("backend"))
  .settings(
    name := "trainmapper-backend"
  )
  .settings(commonSettings)
  .settings(
    resolvers ++= Seq(
      "mygrid" at "http://www.mygrid.org.uk/maven/repository/"
    ),
    libraryDependencies ++= Seq(
      "io.chiv" %% "stompa-fs2" % stompaVersion,
      "com.github.tototoshi" %% "scala-csv" % "1.3.5",
      "uk.org.mygrid.resources.jcoord" % "jcoord" % "1.0",
      "com.typesafe" % "config" % "1.3.3",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "com.github.etaty" %% "rediscala" % "1.8.0")
      ++ Seq(
        "org.http4s"     %% "http4s-circe",
        "org.http4s"     %% "http4s-blaze-server",
        "org.http4s"     %% "http4s-dsl"
      ).map(_ % http4sVersion),
    resources in Compile += (fastOptJS in (frontend, Compile)).value.data,
    resources in Compile += (fastOptJS in (frontend, Compile)).value
      .map((x: sbt.File) => new File(x.getAbsolutePath + ".map"))
      .data,
    (managedResources in Compile) += (artifactPath in (frontend, Compile, packageJSDependencies)).value,
    reStart := (reStart dependsOn (fastOptJS in (frontend, Compile))).evaluated,
    watchSources ++= (watchSources in frontend).value,
//    mainClass in reStart := Some("trainmapper.server.Server")
    mainClass in reStart := Some("trainmapper.Main")
  )
  .dependsOn(sharedJvm)

lazy val frontend = (project in file("frontend"))
  .settings(
    name := "trainmapper-frontend"
  )
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings: _*)
  .settings(
    skip in packageJSDependencies := false,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    crossTarget in (Compile, packageJSDependencies) := (resourceManaged in Compile).value,
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % scalaJsDomV,
      "be.doeraene" %%% "scalajs-jquery" % "0.9.3",
      "io.surfkit" %%% "scalajs-google-maps" % "0.0.3-SNAPSHOT"
    )
  )
  .dependsOn(sharedJs)

scalacOptions ++= Seq("-feature", "-deprecation", "-Ywarn-unused-import", "-Xfatal-warnings", "-language:higherKinds")


