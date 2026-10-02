import com.typesafe.tools.mima.core.*

import xerial.sbt.Sonatype.*
import sbt.ClassLoaderLayeringStrategy
import scoverage.ScoverageKeys.*

val scala3Version = "3.7.4"

/** The MongoDB driver version this build compiles, tests and publishes against.
  *
  * Two defaults rather than one, because the library and the integration suite do not depend on the same artifact and never have: the
  * library needs only `mongo-scala-bson` (and, through it, `org.mongodb:bson`), while the integration suite needs the full
  * `mongo-scala-driver` to talk to a server. Keeping both literals here makes the normal build's resolution explicit.
  *
  * `-Dmongodb.version=X` moves both to X, for the driver compatibility matrix only. A plain `sbt test` needs no properties and no
  * environment variables. Never a range, a `+` or `latest.release`: what gets published must resolve deterministically.
  *
  * Both artifacts are the native Scala 3 builds (`_3`), which MongoDB has published since 5.7.0. That is the floor: no override below
  * 5.7.0 can resolve, because no `_3` artifact exists for it. The previous `CrossVersion.for3Use2_13` shim was removed in 1.0.0 - it
  * made sbt fail hard ("conflicting cross-version suffixes") for anyone who also depended on the official native Scala 3 driver.
  *
  * {{{
  *   sbt test                                  // the defaults below
  *   sbt -Dmongodb.version=5.12.0 test         // one matrix cell
  * }}}
  */
val mongoDbVersionOverride = sys.props.get("mongodb.version")
val mongoScalaBsonVersion = mongoDbVersionOverride.getOrElse("5.12.0")
val mongoScalaDriverVersion = mongoDbVersionOverride.getOrElse("5.12.0")

/** The published artifact every build is checked against for binary compatibility.
  *
  * Pre-1.0 this is the latest release, and the check is an engineering guard only: 0.x carries no compatibility promise, and the 10
  * problems MiMa reports against 0.0.6 are the deliberate API evolution that got us here.
  *
  * The promise starts at 1.0.0. Once 1.0.0 is published, change this one value to "1.0.0" and leave it there for the whole 1.x line, so
  * that 1.3.0 is checked against 1.0.0 rather than against 1.2.0 - otherwise a symbol dropped in 1.2 would never be noticed again.
  */
val binaryCompatibilityBaseline = "0.0.11"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Disable pipelined compilation to avoid macro TASTy classpath issues in tests
ThisBuild / incOptions := (ThisBuild / incOptions).value.withPipelining(false)

ThisBuild / crossScalaVersions := Seq(
  "3.3.1",
  "3.4.2",
  "3.6.3",
  "3.7.1",
  "3.7.4",
  "3.8.0"
)

ThisBuild / conflictManager := ConflictManager.default

ThisBuild / coverageHighlighting := true
ThisBuild / coverageMinimumStmtTotal := 50
ThisBuild / coverageFailOnMinimum := true

usePgpKeyHex("268599B76CCD8B4C")

ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / scalaVersion := scala3Version
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := sonatypePublishToBundle.value

Global / excludeLintKeys += publishMavenStyle

lazy val root = project
  .in(file("."))
  .settings(
    name := "MongoScala3Codec",
    organization := "io.github.mbannour",
    description := "A library for MongoDB BSON codec generation using Scala 3 macros.",
    homepage := Some(url("https://github.com/mbannour/MongoScala3Codec")),
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/mbannour/MongoScala3Codec"),
        "scm:git:git@github.com:mbannour/MongoScala3Codec.git"
      )
    ),
    developers := List(
      Developer(
        id = "medali",
        name = "Mohamed Ali Bannour",
        email = "med.ali.bennour@gmail.com",
        url = url("https://github.com/mbannour/MongoScala3Codec")
      )
    ),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
      "org.scalatestplus" %% "scalacheck-1-19" % "3.2.20.0" % Test,
      "org.mongodb.scala" %% "mongo-scala-bson" % mongoScalaBsonVersion
    ),
    Compile / scalacOptions ++= Seq(
      "-encoding",
      "utf8",
      "-deprecation",
      "-explain-types",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Xcheck-macros",
      "-Yretain-trees",
      "-Wunused:all",
      "-Wconf:msg=unused local definition:s"
    ),
    // The published artifact is built with scala3Version (3.7.x) and targets JVM 11. Scala 3.8 raised its own floor to JVM 17 and
    // rejects -Xtarget:11, so the 3.8 cell of the compiler matrix compiles with that compiler's default target instead.
    Compile / scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, minor)) if minor < 8 => Seq("-Xtarget:11")
      case _                            => Seq.empty
    }),
    Compile / scalacOptions ++= (if (sys.env.contains("CI")) Seq("-Werror") else Seq.empty),
    // No "-rewrite" here. It edits the test sources in place during compilation, which makes a
    // cross-build unsafe to run: `+test` would rewrite the sources under one compiler and then fail
    // to compile them under the next. It was silently deleting `scala.compiletime.testing.typeCheckErrors`
    // imports - the import every negative-compile spec needs - on 3.7.1 but not on 3.7.4.
    Test / scalacOptions ++= Seq(
      "-Wconf:cat=unused:s"
    ),
    Test / scalacOptions ~= (_.filterNot(Set("-Werror", "-Xfatal-warnings"))),
    Compile / doc / scalacOptions ++= Seq(
      "-nowarn",
      "-Wconf:any:s"
    ),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
    Test / publishArtifact := false,
    mimaPreviousArtifacts := Set(
      organization.value %% moduleName.value % binaryCompatibilityBaseline
    ),
    // A missing baseline is a broken check, not a passing one.
    mimaFailOnNoPrevious := true,
    // Deliberately empty. An exclusion here silences a real report, so each one must name the symbol it
    // covers and say why the break is acceptable - never a package-wide or problem-class-wide filter.
    mimaBinaryIssueFilters := Seq(
      // 1.0.0: `from` and `newBuilder` gained a `(using CodecConfig)` clause so an in-scope `given CodecConfig` is honoured instead of
      // silently dropped. Source-compatible (the default argument keeps existing call sites working); binary-breaking against 0.0.11,
      // which carries no compatibility promise. Remove both once the baseline moves to 1.0.0.
      ProblemFilters.exclude[DirectMissingMethodProblem]("io.github.mbannour.mongo.codecs.RegistryBuilder#package#RegistryBuilder.from"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("io.github.mbannour.mongo.codecs.RegistryBuilder#package#RegistryBuilder.newBuilder")
    )
  )

lazy val integrationTests = project
  .in(file("integration"))
  .dependsOn(root)
  .settings(
    name := "integration-tests",
    // Limit cross-building here to the primary Scala version to avoid test dep gaps
    crossScalaVersions := Seq(scala3Version),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalactic" %% "scalactic" % "3.2.19" % Test,
      "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
      "org.scalatestplus" %% "scalacheck-1-19" % "3.2.20.0" % Test,
      "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.44.0" % Test,
      "com.dimafeng" %% "testcontainers-scala-mongodb" % "0.44.0" % Test,
      "org.mongodb.scala" %% "mongo-scala-driver" % mongoScalaDriverVersion
    ),
    testFrameworks += new TestFramework("org.scalatest.tools.Framework"),
    fork := true,
    Test / parallelExecution := false,
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty,
    mimaFailOnNoPrevious := false
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(root)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "MongoScala3Codec-benchmarks",
    crossScalaVersions := Seq(scala3Version),
    publish / skip := true,
    Test / skip := true,
    fork := true,
    mimaPreviousArtifacts := Set.empty,
    mimaFailOnNoPrevious := false
  )

lazy val examples = project
  .in(file("examples"))
  .dependsOn(root)
  .settings(
    name := "MongoScala3Codec-examples",
    crossScalaVersions := Seq(scala3Version),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.21",
      "org.slf4j" % "slf4j-api" % "2.0.17",
      "org.mongodb.scala" %% "mongo-scala-driver" % mongoScalaDriverVersion
    ),
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty,
    mimaFailOnNoPrevious := false,
    Compile / run / fork := true
  )
