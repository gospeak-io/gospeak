ThisBuild / version := "0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.10"
ThisBuild / organization := "gospeak"

ThisBuild / fork := true
ThisBuild / javaOptions += "-Xmx1G"


/**
 * Global settings
 */
val commonSettings: Seq[Setting[_]] = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-Ypartial-unification"),
  resolvers ++= Seq(Resolver.jcenterRepo),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)


/**
 * Dependencies
 */

val cats = Seq(
  "org.typelevel" %% "cats-core" % "2.1.0",
  "org.typelevel" %% "cats-effect" % "2.1.1")
val circeVersion = "0.13.0"
val circe = Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-literal" % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,
  "io.circe" %% "circe-java8" % "0.11.1")
val doobieVersion = "0.8.6" // "0.8.7" version causes: Can't infer the SQL type to use for an instance of java.time.Instant. Use setObject() with an explicit Types value to specify the type to use.
val doobie = Seq(
  "org.tpolecat" %% "doobie-core",
  "org.tpolecat" %% "doobie-h2",
  "org.tpolecat" %% "doobie-postgres",
  "org.tpolecat" %% "doobie-hikari").map(_ % doobieVersion)
val doobieTest = Seq(
  "org.tpolecat" %% "doobie-scalatest" % doobieVersion).map(_ % Test)
val pureconfig = Seq("com.github.pureconfig" %% "pureconfig" % "0.12.2")
val hammock = Seq(
  "com.pepegar" %% "hammock-core",
  "com.pepegar" %% "hammock-circe",
  "com.pepegar" %% "hammock-apache-http").map(_ % "0.10.0")
val akka = Seq("com.typesafe.akka" %% "akka-http" % "10.1.11")
val twitter = Seq("com.danielasfregola" %% "twitter4s" % "6.2") // https://github.com/DanielaSfregola/twitter4s
val flyway = Seq("org.flywaydb" % "flyway-core" % "6.3.1")
val silhouetteVersion = "6.1.1"
val silhouette = Seq(
  "com.mohiva" %% "play-silhouette",
  "com.mohiva" %% "play-silhouette-password-bcrypt",
  "com.mohiva" %% "play-silhouette-crypto-jca",
  "com.mohiva" %% "play-silhouette-persistence").map(_ % silhouetteVersion)
val silhouetteTest = Seq(
  "com.mohiva" %% "play-silhouette-testkit").map(_ % silhouetteVersion % "test")
val playJson = Seq("com.typesafe.play" %% "play-json" % "2.8.1")
val macwireVersion = "2.3.3"
val play = Seq(
  "com.softwaremill.macwire" %% "macros" % macwireVersion % Provided,
  "com.softwaremill.macwire" %% "macrosakka" % macwireVersion % Provided)
val playTest = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0").map(_ % Test)
val flexmark = Seq("com.vladsch.flexmark" % "flexmark-all" % "0.50.50")
val mustache = Seq("com.github.eikek" %% "yamusca-core" % "0.6.1")
val sendgrid = Seq("com.sendgrid" % "sendgrid-java" % "4.4.5")
val webjars = Seq( // available in web/target/web/web-modules/main/webjars/lib folder
  "org.webjars.npm" % "jquery" % "3.4.1",
  "org.webjars.npm" % "bootstrap" % "4.4.1",
  "org.webjars.npm" % "autosize" % "4.0.2",
  // "org.webjars.npm" % "@fortawesome/fontawesome-free" % "5.6.3",
  "org.webjars.npm" % "select2" % "4.0.13",
  "org.webjars.npm" % "select2-bootstrap-theme" % "0.1.0-beta.10",
  "org.webjars.npm" % "bootstrap-datepicker" % "1.9.0",
  "org.webjars.npm" % "imask" % "5.2.1",
  "org.webjars.npm" % "github-com-twitter-typeahead-js" % "0.11.1",
  "org.webjars.npm" % "typeahead.js-bootstrap4-css" % "1.0.0",
  "org.webjars.npm" % "mousetrap" % "1.6.5",
  "org.webjars.npm" % "swagger-ui-dist" % "3.25.0")
val logback = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "ch.qos.logback" % "logback-classic" % "1.2.3")
val scalaTest = Seq(
  "org.scalatest" %% "scalatest" % "3.0.8").map(_ % Test)
val scalaCheck = Seq(
  "org.scalacheck" %% "scalacheck" % "1.14.3",
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.4",
  "com.danielasfregola" %% "random-data-generator" % "2.8").map(_ % Test)

val libsDependencies = hammock ++ flexmark ++ mustache ++ cats ++ playJson ++ scalaTest ++ scalaCheck
val coreDependencies = cats ++ scalaTest ++ scalaCheck
val infraDependencies = twitter ++ akka ++ flexmark ++ mustache ++ sendgrid ++ circe ++ doobie ++ flyway ++ scalaTest ++ scalaCheck ++ doobieTest
val webDependencies = play ++ silhouette ++ pureconfig ++ webjars ++ logback ++ scalaTest ++ scalaCheck ++ playTest ++ silhouetteTest


/**
 * Project definition
 */
val libs = (project in file("libs"))
  .settings(
    name := "libs",
    libraryDependencies ++= libsDependencies,
    commonSettings
  )

val core = (project in file("core"))
  .dependsOn(libs)
  .settings(
    name := "core",
    libraryDependencies ++= coreDependencies,
    commonSettings
  )

val infra = (project in file("infra"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "infra",
    libraryDependencies ++= infraDependencies,
    commonSettings
  )

val web = (project in file("web"))
  .enablePlugins(PlayScala, BuildInfoPlugin)
  .dependsOn(core % "compile->compile;test->test", infra)
  .settings(
    name := "web",
    libraryDependencies ++= webDependencies ++ Seq(ws),
    routesImport ++= Seq(
      "gospeak.core.domain._",
      "gospeak.libs.scala.domain._",
      "gospeak.core.services.meetup.domain._",
      "gospeak.web.utils.PathBindables._",
      "gospeak.web.utils.QueryStringBindables._"),
    buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion,
      // see https://www.git-scm.com/docs/git-log#_pretty_formats
      "gitBranch" -> execOutput("git rev-parse --abbrev-ref HEAD"),
      "gitHash" -> execOutput("git log -1 --format=%h"),
      "gitAuthorName" -> execOutput("git log -1 --format=%an"),
      "gitAuthorEmail" -> execOutput("git log -1 --format=%ae"),
      "gitAuthorDate" -> execOutput("git log -1 --format=%at"),
      "gitCommitterName" -> execOutput("git log -1 --format=%cn"),
      "gitCommitterEmail" -> execOutput("git log -1 --format=%ce"),
      "gitCommitterDate" -> execOutput("git log -1 --format=%ct"),
      "gitSubject" -> execOutput("git log -1 --format=%s")),
    buildInfoPackage := "generated",
    buildInfoOptions ++= Seq(BuildInfoOption.BuildTime),
    commonSettings
  )

def execOutput(command: String): String = {
  try {
    val extracted = new java.io.InputStreamReader(java.lang.Runtime.getRuntime.exec(command).getInputStream)
    Option(new java.io.BufferedReader(extracted).readLine()).getOrElse(s"No result for '$command'")
  } catch {
    case t: Throwable => s"'$command' failed: ${t.getMessage}"
  }
}

val global = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(JavaAppPackaging)
  .dependsOn(web)
  .aggregate(libs, core, infra, web) // send commands to every module
  .settings(name := "gospeak")

// rename zip file created from `dist` command
packageName in Universal := "gospeak"

// needed to parallelise tests in circleci: https://tanin.nanakorn.com/technical/2018/09/10/parallelise-tests-in-sbt-on-circle-ci.html
val printTests = taskKey[Unit]("Print full class names of tests to the file `test-full-class-names.log`.")
printTests := {
  import java.io._

  println("Print full class names of tests to the file `test-full-class-names.log`.")

  val pw = new PrintWriter(new File("test-full-class-names.log"))
  (definedTests in Test).value.sortBy(_.name).foreach { t =>
    pw.println(t.name)
  }
  pw.close()
}
