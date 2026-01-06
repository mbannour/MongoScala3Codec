addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.5")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.3")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")
// JMH benchmarking plugin - using latest version for better Scala 3 support
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")
