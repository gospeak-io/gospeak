logLevel := Level.Warn

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases"
resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/maven-releases"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.15")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.15")
addSbtPlugin("org.irundaia.sbt" % "sbt-sassify" % "1.4.13") // cf https://github.com/irundaia/sbt-sassify
addSbtPlugin("com.arpnetworking" % "sbt-typescript" % "0.4.2") // cf https://github.com/ArpNetworking/sbt-typescript
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "3.0.3")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.0")
addSbtPlugin("au.com.onegeek" %% "sbt-dotenv" % "2.1.146") // to load environment variables from `.env` file (cf https://github.com/mefellows/sbt-dotenv)
