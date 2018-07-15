import sbt.Keys.libraryDependencies
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtcrossproject.CrossType

val stompaVersion = "0.1.0-SNAPSHOT"
val scalaLoggingVersion = "3.5.0"
val circeVersion = "0.9.0"
val http4sVersion = "0.18.12"
val doobieVersion  = "0.5.3"
val scalaJsDomV = "0.9.6"
val scalaTagsV = "0.6.7"
val buckyVersion = "1.3.1"

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

lazy val backendMessageReceiver = (project in file("message-receiver"))
  .settings(
    name := "trainmapper-message-receiver"
  ) .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.chiv" %% "stompa-fs2" % stompaVersion,
      "com.typesafe" % "config" % "1.3.3",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "net.logstash.logback"       % "logstash-logback-encoder"  % "4.6",
      "ch.qos.logback"             % "logback-classic"           % "1.1.5",
      "com.typesafe.scala-logging" %% "scala-logging"            % "3.5.0",
      "com.itv" %% "bucky-core" % buckyVersion,
      "com.itv" %% "bucky-rabbitmq" % buckyVersion,
      "com.itv" %% "bucky-fs2" % buckyVersion,
      "com.itv" %% "bucky-circe" % buckyVersion,
      "com.itv" %% "bucky-test"  % buckyVersion  % "test"),
    mainClass in (Compile, run) := Some("trainmapper.MessageReceiverMain")
  ).dependsOn(sharedJvm)

lazy val activationMessageHandler = (project in file("activation-message-handler"))
  .settings(
    name := "trainmapper--activation-message-handler"
  ) .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.3.3",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "net.logstash.logback"       % "logstash-logback-encoder"  % "4.6",
      "ch.qos.logback"             % "logback-classic"           % "1.1.5",
      "com.typesafe.scala-logging" %% "scala-logging"            % "3.5.0",
      "com.github.etaty" %% "rediscala" % "1.8.0",
      "com.itv" %% "bucky-core" % buckyVersion,
      "com.itv" %% "bucky-rabbitmq" % buckyVersion,
      "com.itv" %% "bucky-fs2" % buckyVersion,
      "com.itv" %% "bucky-circe" % buckyVersion,
      "com.itv" %% "bucky-test"  % buckyVersion  % "test"),
    mainClass in (Compile, run) := Some("trainmapper.ActivationMessageHandlerMain")
  ).dependsOn(sharedJvm)


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
      "org.flywaydb" % "flyway-core" % "5.1.1",
      "io.circe" %% "circe-fs2" % circeVersion)
      ++ Seq(
      "org.tpolecat"               %% "doobie-core"             % doobieVersion,
      "org.tpolecat"               %% "doobie-hikari"           % doobieVersion,
      "org.tpolecat"               %% "doobie-postgres"         % doobieVersion,
      "org.tpolecat"               %% "doobie-scalatest"        % doobieVersion % "test",
      "org.tpolecat"               %% "doobie-h2"               % doobieVersion % "test"
    )
      ++ Seq(
        "org.http4s"     %% "http4s-circe",
        "org.http4s"     %% "http4s-blaze-server",
        "org.http4s"     %% "http4s-dsl",
      "org.http4s" %% "http4s-blaze-client"
      ).map(_ % http4sVersion),
    resources in Compile += (fastOptJS in (frontend, Compile)).value.data,
    resources in Compile += (fastOptJS in (frontend, Compile)).value
      .map((x: sbt.File) => new File(x.getAbsolutePath + ".map"))
      .data,
    (managedResources in Compile) += (artifactPath in (frontend, Compile, packageJSDependencies)).value,
    reStart := (reStart dependsOn (fastOptJS in (frontend, Compile))).evaluated,
    watchSources ++= (watchSources in frontend).value,
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


