resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/maven-releases"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.4.1")
addSbtPlugin("org.irundaia.sbt" % "sbt-sassify" % "1.4.13") // cf https://github.com/irundaia/sbt-sassify
addSbtPlugin("com.arpnetworking" % "sbt-typescript" % "0.4.2") // cf https://github.com/ArpNetworking/sbt-typescript
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "2.112")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
