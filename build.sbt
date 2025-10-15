import xerial.sbt.Sonatype.*

val scala3Version = "3.7.1"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / crossScalaVersions := Seq(
  "3.3.1",
  "3.4.0",
  "3.4.1",
  "3.4.2",
  "3.5.0",
  "3.5.1",
  "3.5.2",
  "3.6.3",
  "3.6.4",
  "3.7.1"
)

usePgpKeyHex("8D15E6EFEC642C76")
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / scalaVersion := scala3Version
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := sonatypePublishToBundle.value

lazy val root = project
  .in(file("."))
  .settings(
    name := "MongoScala3Codec",
    organization := "io.github.mbannour",
    version := "0.0.6",
    description := "A library for MongoDB BSON codec generation using Scala 3 macros.",
    homepage := Some(url("https://github.com/mbannour/MongoScala3Codec")),
    licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
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
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalacheck" %% "scalacheck" % "1.18.0" % Test,
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test,
      ("org.mongodb.scala" %% "mongo-scala-bson" % "5.5.2").cross(CrossVersion.for3Use2_13)
    ),
    scalacOptions ++= Seq(
      "-encoding",
      "utf8",
      "-deprecation",
      "-explain-types",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-Xtarget:11",
      "-unchecked",
      "-Ykind-projector",
      "-Xcheck-macros",
      "-Yretain-trees",
      "-Wunused:all"
    ),
    // Fatal warnings on CI
    scalacOptions ++= (if (sys.env.get("CI").isDefined) Seq("-Werror") else Seq.empty),
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
    Test / publishArtifact := false
  )

lazy val integrationTests = project
  .in(file("integration"))
  .dependsOn(root)
  .settings(
    name := "integration-tests",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalactic" %% "scalactic" % "3.2.19" % Test,
      "org.scalacheck" %% "scalacheck" % "1.18.0" % Test,
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test,
      "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.4" % Test,
      "com.dimafeng" %% "testcontainers-scala-mongodb" % "0.41.4" % Test,
      ("org.mongodb.scala" %% "mongo-scala-driver" % "5.2.1").cross(CrossVersion.for3Use2_13)
    ),
    testFrameworks += new TestFramework("org.scalatest.tools.Framework"),
    fork := true,
    Test / parallelExecution := false,
    publish / skip := true
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(root)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "MongoScala3Codec-benchmarks",
    publish / skip := true,
    Test / skip := true,
    // Inherit scalaVersion/cross from ThisBuild; we only run on default in CI smoke
    // Keep JMH runs fast locally by default; override in CLI as needed
    fork := true
  )

lazy val rootProject = project
  .in(file("."))
  .aggregate(root, integrationTests, benchmarks)
  .settings(
    publish / skip := true
  )
