import xerial.sbt.Sonatype.*
import sbt.ClassLoaderLayeringStrategy
import scoverage.ScoverageKeys.*

val scala3Version = "3.7.4"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Disable pipelined compilation to avoid macro TASTy classpath issues in tests
ThisBuild / incOptions := (ThisBuild / incOptions).value.withPipelining(false)

ThisBuild / crossScalaVersions := Seq(
  "3.3.1",
  "3.4.2",
  "3.6.4",
  "3.7.1"
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

lazy val publishSettings = Seq(
  organization  := "io.github.mbannour",
  version       := "0.0.11",
  homepage      := Some(url("https://github.com/mbannour/MongoScala3Codec")),
  licenses      += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/mbannour/MongoScala3Codec"),
      "scm:git:git@github.com:mbannour/MongoScala3Codec.git"
    )
  ),
  developers := List(
    Developer(
      id    = "medali",
      name  = "Mohamed Ali Bannour",
      email = "med.ali.bennour@gmail.com",
      url   = url("https://github.com/mbannour/MongoScala3Codec")
    )
  ),
  credentials      += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
  Test / publishArtifact := false,
  mimaFailOnNoPrevious   := false
)

lazy val compileSettings = Seq(
  Compile / scalacOptions ++= Seq(
    "-encoding",
    "utf8",
    "-deprecation",
    "-explain-types",
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-Xtarget:11",
    "-unchecked",
    "-Xcheck-macros",
    "-Yretain-trees",
    "-Wunused:all",
    "-Wconf:msg=unused local definition:s"
  ),
  Compile / scalacOptions ++= (if (sys.env.contains("CI")) Seq("-Werror") else Seq.empty),
  Test / scalacOptions ++= Seq(
    "-Wconf:cat=unused:s"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 4 =>
      Seq("-rewrite", "-source", "3.4-migration")
    case _ =>
      Seq.empty
  }),
  Test / scalacOptions ~= (_.filterNot(Set("-Werror", "-Xfatal-warnings"))),
  Compile / doc / scalacOptions ++= Seq(
    "-nowarn",
    "-Wconf:any:s"
  ),
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
)

lazy val root = project
  .in(file("."))
  .settings(
    publishSettings,
    compileSettings,
    name        := "MongoScala3Codec",
    description := "A library for MongoDB BSON codec generation using Scala 3 macros.",
    libraryDependencies ++= Seq(
      "org.scalatest"     %% "scalatest"           % "3.2.20"  % Test,
      "org.scalacheck"    %% "scalacheck"           % "1.19.0"  % Test,
      "org.scalatestplus" %% "scalacheck-1-18"      % "3.2.19.0" % Test,
      "org.mongodb.scala" %% "mongo-scala-bson" % "5.7.0"
    ),
    mimaPreviousArtifacts := Set(
      organization.value %% moduleName.value % "0.0.10"
    )
  )

lazy val dsl = project
  .in(file("dsl"))
  .dependsOn(root)
  .settings(
    publishSettings,
    compileSettings,
    name        := "mongoscala3codec-dsl",
    description := "Type-safe query/update DSL for MongoScala3Codec — compile-time filters, updates, sorts, and projections.",
    libraryDependencies ++= Seq(
      "org.scalatest"     %% "scalatest"      % "3.2.20"  % Test,
      "org.scalacheck"    %% "scalacheck"      % "1.19.0"  % Test,
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test
    ),
    mimaPreviousArtifacts := Set.empty
  )

lazy val dslTestkit = project
  .in(file("dsl-testkit"))
  .dependsOn(dsl)
  .settings(
    publishSettings,
    compileSettings,
    name        := "mongoscala3codec-dsl-testkit",
    description := "Testing utilities for code using the MongoScala3Codec DSL.",
    libraryDependencies ++= Seq(
      "org.scalatest"     %% "scalatest"      % "3.2.20",
      "org.scalacheck"    %% "scalacheck"      % "1.19.0"  % Test,
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test
    ),
    mimaPreviousArtifacts := Set.empty
  )

lazy val repository = project
  .in(file("repository"))
  .dependsOn(root, dsl)
  .settings(
    publishSettings,
    compileSettings,
    name        := "mongoscala3codec-repository",
    description := "Type-safe, effect-agnostic MongoDB repository abstraction built on the MongoScala3Codec codecs and DSL.",
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % "5.7.0",
      "org.scalatest"     %% "scalatest"      % "3.2.20"  % Test,
      "org.scalacheck"    %% "scalacheck"      % "1.19.0"  % Test,
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test
    ),
    mimaPreviousArtifacts := Set.empty
  )

lazy val integrationTests = project
  .in(file("integration"))
  .dependsOn(root, dsl, dslTestkit, repository)
  .settings(
    name := "integration-tests",
    // Limit cross-building here to the primary Scala version to avoid test dep gaps
    crossScalaVersions := Seq(scala3Version),
    libraryDependencies ++= Seq(
      "org.scalatest"     %% "scalatest"                       % "3.2.20"  % Test,
      "org.scalactic"     %% "scalactic"                       % "3.2.19"  % Test,
      "org.scalacheck"    %% "scalacheck"                      % "1.19.0"  % Test,
      "org.scalatestplus" %% "scalacheck-1-18"                 % "3.2.19.0" % Test,
      "com.dimafeng"      %% "testcontainers-scala-scalatest"  % "0.44.0"  % Test,
      "com.dimafeng"      %% "testcontainers-scala-mongodb"    % "0.44.0"  % Test,
      "org.mongodb.scala" %% "mongo-scala-driver" % "5.7.0"
    ),
    testFrameworks += new TestFramework("org.scalatest.tools.Framework"),
    fork := true,
    Test / parallelExecution := false,
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(root)
  .enablePlugins(JmhPlugin)
  .settings(
    name               := "MongoScala3Codec-benchmarks",
    crossScalaVersions := Seq(scala3Version),
    publish / skip     := true,
    Test / skip        := true,
    fork               := true,
    mimaPreviousArtifacts := Set.empty
  )

lazy val examples = project
  .in(file("examples"))
  .dependsOn(root)
  .settings(
    name               := "MongoScala3Codec-examples",
    crossScalaVersions := Seq(scala3Version),
    libraryDependencies ++= Seq(
      "ch.qos.logback"  %  "logback-classic"  % "1.5.21",
      "org.slf4j"       %  "slf4j-api"        % "2.0.17",
      "org.mongodb.scala" %% "mongo-scala-driver" % "5.7.0"
    ),
    publish / skip        := true,
    mimaPreviousArtifacts := Set.empty,
    Compile / run / fork  := true
  )
