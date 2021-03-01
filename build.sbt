ThisBuild / version := "0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.12"
ThisBuild / organization := "gospeak"

/**
 * See .jmvopts for more options
 * - xms: memory to use when start jvm
 * - xmx: max memory available for the jvm
 */


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
  "org.typelevel" %% "cats-core" % "2.1.1",
  "org.typelevel" %% "cats-effect" % "2.1.4")
val circeVersion = "0.13.0"
val circe = Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-literal" % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,
  "io.circe" %% "circe-java8" % "0.11.1")
val doobieVersion = "0.9.2"
val doobie = Seq(
  "org.tpolecat" %% "doobie-core",
  "org.tpolecat" %% "doobie-h2",
  "org.tpolecat" %% "doobie-postgres",
  "org.tpolecat" %% "doobie-hikari").map(_ % doobieVersion)
val doobieTest = Seq(
  "org.tpolecat" %% "doobie-scalatest" % doobieVersion).map(_ % Test)
val pureconfig = Seq("com.github.pureconfig" %% "pureconfig" % "0.13.0")
val hammock = Seq(
  "com.pepegar" %% "hammock-core",
  "com.pepegar" %% "hammock-circe",
  "com.pepegar" %% "hammock-apache-http").map(_ % "0.11.0")
val youtubeApi = Seq(
  "com.google.apis" % "google-api-services-youtube" % "v3-rev20200618-1.30.9",
  "com.google.api-client" % "google-api-client" % "1.23.1",
  "com.google.oauth-client" % "google-oauth-client-jetty" % "1.31.0"
)
val akka = Seq("com.typesafe.akka" %% "akka-http" % "10.1.12")
val twitter = Seq("com.danielasfregola" %% "twitter4s" % "6.2") // https://github.com/DanielaSfregola/twitter4s
val googleMaps = Seq("com.google.maps" % "google-maps-services" % "0.14.0") // https://github.com/googlemaps/google-maps-services-java
val flyway = Seq("org.flywaydb" % "flyway-core" % "6.4.4")
val cron = Seq(
  "eu.timepit" %% "fs2-cron-core" % "0.2.2",
  "com.github.pureconfig" %% "pureconfig-cron4s" % "0.13.0")
val silhouetteVersion = "7.0.0"
val silhouette = Seq(
  "com.mohiva" %% "play-silhouette",
  "com.mohiva" %% "play-silhouette-password-bcrypt",
  "com.mohiva" %% "play-silhouette-crypto-jca",
  "com.mohiva" %% "play-silhouette-persistence").map(_ % silhouetteVersion)
val silhouetteTest = Seq(
  "com.mohiva" %% "play-silhouette-testkit").map(_ % silhouetteVersion % "test")
val playJson = Seq("com.typesafe.play" %% "play-json" % "2.9.0")
val macwireVersion = "2.3.7"
val play = Seq(
  "com.softwaremill.macwire" %% "macros" % macwireVersion % Provided,
  "com.softwaremill.macwire" %% "macrosakka" % macwireVersion % Provided)
val playTest = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0").map(_ % Test)
val flexmark = Seq("com.vladsch.flexmark" % "flexmark-all" % "0.61.34")
val mustache = Seq("com.github.eikek" %% "yamusca-core" % "0.6.2")
val liquid = Seq("nl.big-o" % "liqp" % "0.7.9")
val sendgrid = Seq("com.sendgrid" % "sendgrid-java" % "4.6.1")
val safeql = Seq("fr.loicknuchel" %% "safeql" % "0.1.3")
val typedapi = Seq("fr.loicknuchel" %% "typedapi" % "0.1.0")
val webjars = Seq( // available in web/target/web/web-modules/main/webjars/lib folder
  "org.webjars.npm" % "jquery" % "3.5.1",
  "org.webjars.npm" % "bootstrap" % "4.5.0",
  "org.webjars.npm" % "autosize" % "4.0.2",
  // "org.webjars.npm" % "@fortawesome/fontawesome-free" % "5.6.3",
  "org.webjars.npm" % "select2" % "4.0.13",
  "org.webjars.npm" % "select2-bootstrap-theme" % "0.1.0-beta.10",
  "org.webjars.npm" % "bootstrap-datepicker" % "1.9.0",
  "org.webjars.npm" % "imask" % "6.0.7",
  "org.webjars.npm" % "github-com-twitter-typeahead-js" % "0.11.1",
  "org.webjars.npm" % "typeahead.js-bootstrap4-css" % "1.0.0",
  "org.webjars.npm" % "mousetrap" % "1.6.5",
  "org.webjars.npm" % "swagger-ui-dist" % "3.25.5")
val logback = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "ch.qos.logback" % "logback-classic" % "1.2.3")
val scalaTest = Seq(
  "org.scalatest" %% "scalatest" % "3.1.2",
  "org.scalamock" %% "scalamock" % "4.4.0",
  "org.scalatestplus" %% "scalacheck-1-14" % "3.1.0.1").map(_ % Test)
val scalaCheck = Seq(
  "org.scalacheck" %% "scalacheck" % "1.14.3",
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.5",
  "com.danielasfregola" %% "random-data-generator" % "2.8").map(_ % Test)

val libsDependencies = cats ++ doobie ++ flyway ++ hammock ++ flexmark ++ mustache ++ liquid ++ googleMaps ++ youtubeApi ++ playJson ++ scalaTest ++ scalaCheck ++ doobieTest
val coreDependencies = cats ++ scalaTest ++ scalaCheck
val infraDependencies = twitter ++ akka ++ sendgrid ++ safeql ++ circe ++ scalaTest ++ scalaCheck ++ doobieTest
val webDependencies = play ++ silhouette ++ pureconfig ++ cron ++ typedapi ++ webjars ++ logback ++ scalaTest ++ scalaCheck ++ playTest ++ silhouetteTest


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
