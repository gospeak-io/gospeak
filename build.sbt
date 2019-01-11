ThisBuild / version := "0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "fr.gospeak"


/**
  * Global settings
  */
val commonSettings: Seq[Setting[_]] = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-Ypartial-unification"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0-M4")
)


/**
  * Dependencies
  */
val cats = Seq(
  "org.typelevel" %% "cats-core" % "1.4.0",
  "org.typelevel" %% "cats-effect" % "0.10.1")
val circe = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-literal",
  "io.circe" %% "circe-java8").map(_ % "0.10.1")
val doobieVersion = "0.5.3" // 0.6.0 depends on fs2 1.0.0 which is incompatible with http4s 0.18.21
val doobie = Seq(
  "org.tpolecat" %% "doobie-core",
  "org.tpolecat" %% "doobie-h2",
  "org.tpolecat" %% "doobie-postgres").map(_ % doobieVersion)
val doobieTest = Seq(
  "org.tpolecat" %% "doobie-scalatest" % doobieVersion).map(_ % Test)
val flyway = Seq(
  "org.flywaydb" % "flyway-core" % "5.1.4")
val macwireVersion = "2.3.1"
val play = Seq(
  "com.softwaremill.macwire" %% "macros" % macwireVersion % Provided,
  "com.softwaremill.macwire" %% "macrosakka" % macwireVersion % Provided)
val playTest = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2").map(_ % Test)
val webjars = Seq(
  "org.webjars.npm" % "jquery" % "3.3.1",
  "org.webjars.npm" % "bootstrap" % "4.1.3")
val logback = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "ch.qos.logback" % "logback-classic" % "1.2.3")
val scalaTest = Seq(
  "org.scalatest" %% "scalatest" % "3.0.5").map(_ % Test)
val scalaCheck = Seq(
  "org.scalacheck" %% "scalacheck" % "1.14.0",
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.0",
  "com.danielasfregola" %% "random-data-generator" % "2.6").map(_ % Test)

val scalautilsDependencies = cats ++ scalaTest
val coreDependencies = cats ++ scalaTest
val infraDependencies = circe ++ doobie ++ flyway ++ scalaTest ++ scalaCheck ++ doobieTest
val webDependencies = play ++ webjars ++ logback ++ scalaTest ++ scalaCheck ++ playTest


/**
  * Project definition
  */
val scalautils = (project in file("libs/scalautils"))
  .settings(
    name := "scalautils",
    libraryDependencies ++= scalautilsDependencies,
    commonSettings
  )

val core = (project in file("core"))
  .dependsOn(scalautils)
  .settings(
    name := "core",
    libraryDependencies ++= coreDependencies,
    commonSettings
  )

val infra = (project in file("infra"))
  .dependsOn(core)
  .settings(
    name := "infra",
    libraryDependencies ++= infraDependencies,
    commonSettings
  )

val web = (project in file("web"))
  .enablePlugins(PlayScala)
  .dependsOn(core, infra)
  .settings(
    name := "web",
    libraryDependencies ++= webDependencies,
    commonSettings
  )

val global = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(web)
  .aggregate(scalautils, core, infra, web) // send commands to every module
  .settings(name := "gospeak")
