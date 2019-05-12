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
  resolvers ++= Seq(Resolver.jcenterRepo),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0-M4")
)


/**
  * Dependencies
  */
val cats = Seq(
  "org.typelevel" %% "cats-core" % "1.5.0",
  "org.typelevel" %% "cats-effect" % "1.1.0")
val circe = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-literal",
  "io.circe" %% "circe-generic-extras",
  "io.circe" %% "circe-java8").map(_ % "0.10.1")
val doobieVersion = "0.6.0"
val doobie = Seq(
  "org.tpolecat" %% "doobie-core",
  "org.tpolecat" %% "doobie-h2",
  "org.tpolecat" %% "doobie-postgres",
  "org.tpolecat" %% "doobie-hikari").map(_ % doobieVersion)
val doobieTest = Seq(
  "org.tpolecat" %% "doobie-scalatest" % doobieVersion).map(_ % Test)
val mongo = Seq("org.reactivemongo" %% "reactivemongo" % "0.16.5")
val pureconfig = Seq("com.github.pureconfig" %% "pureconfig" % "0.10.2")
val hammock = Seq(
  "com.pepegar" %% "hammock-core",
  "com.pepegar" %% "hammock-circe",
  "com.pepegar" %% "hammock-apache-http").map(_ % "0.9.0")
val flyway = Seq("org.flywaydb" % "flyway-core" % "5.1.4")
val silhouetteVersion = "5.0.7"
val silhouette = Seq(
  "com.mohiva" %% "play-silhouette",
  "com.mohiva" %% "play-silhouette-password-bcrypt",
  "com.mohiva" %% "play-silhouette-crypto-jca",
  "com.mohiva" %% "play-silhouette-persistence").map(_ % silhouetteVersion)
val silhouetteTest = Seq(
  "com.mohiva" %% "play-silhouette-testkit").map(_ % silhouetteVersion % "test")
val macwireVersion = "2.3.1"
val play = Seq(
  "com.softwaremill.macwire" %% "macros" % macwireVersion % Provided,
  "com.softwaremill.macwire" %% "macrosakka" % macwireVersion % Provided)
val playTest = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2").map(_ % Test)
val flexmark = Seq("com.vladsch.flexmark" % "flexmark-all" % "0.40.16")
val mustache = Seq("com.github.eikek" %% "yamusca-core" % "0.5.1")
val sendgrid = Seq("com.sendgrid" % "sendgrid-java" % "4.3.0")
val webjars = Seq( // available in web/target/web/web-modules/main/webjars/lib folder
  "org.webjars.npm" % "jquery" % "3.3.1",
  "org.webjars.npm" % "bootstrap" % "4.3.1",
  "org.webjars.npm" % "autosize" % "4.0.2",
  // "org.webjars.npm" % "@fortawesome/fontawesome-free" % "5.6.3",
  "org.webjars.npm" % "select2" % "4.0.5",
  "org.webjars.npm" % "select2-bootstrap-theme" % "0.1.0-beta.10")
val logback = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "ch.qos.logback" % "logback-classic" % "1.2.3")
val scalaTest = Seq(
  "org.scalatest" %% "scalatest" % "3.0.5").map(_ % Test)
val scalaCheck = Seq(
  "org.scalacheck" %% "scalacheck" % "1.14.0",
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.0",
  "com.danielasfregola" %% "random-data-generator" % "2.6").map(_ % Test)

val scalautilsDependencies = cats ++ scalaTest ++ scalaCheck
val coreDependencies = cats ++ scalaTest ++ scalaCheck
val infraDependencies = hammock ++ flexmark ++ mustache ++ sendgrid ++ circe ++ doobie ++ flyway ++ scalaTest ++ scalaCheck ++ doobieTest
val migrationDependencies = mongo ++ logback ++ scalaTest ++ scalaCheck
val webDependencies = play ++ silhouette ++ pureconfig ++ webjars ++ logback ++ scalaTest ++ scalaCheck ++ playTest ++ silhouetteTest


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

val migration = (project in file("migration"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "migration",
    libraryDependencies ++= migrationDependencies,
    commonSettings
  )

val infra = (project in file("infra"))
  .dependsOn(core % "compile->compile;test->test", migration)
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
      "fr.gospeak.core.domain._",
      "fr.gospeak.libs.scalautils.domain._",
      "fr.gospeak.web.utils.PathBindables._",
      "fr.gospeak.web.utils.QueryStringBindables._"),
    buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion, buildInfoBuildNumber,
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
  .aggregate(scalautils, core, infra, web) // send commands to every module
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
