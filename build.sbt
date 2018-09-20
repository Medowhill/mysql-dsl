import Keys._

val buildSettings = Defaults.coreDefaultSettings ++ Seq(
  organization := "services.xis.mysql",
  version := "0.1.0",
  scalaVersion := "2.12.6",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

lazy val root = (project in file(".")).
  settings(buildSettings ++ Seq(
    name := "mysql-dsl",
    test := test in Compile in tests,
  )
) aggregate(macros, tests)

lazy val macros: Project = (project in file("macros")).
  settings(buildSettings ++ Seq(
    libraryDependencies += "org.typelevel" %% "macro-compat" % "1.1.1",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    libraryDependencies += scalaVersion("org.scala-lang" % "scala-reflect" % _).value,
    libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.12"
  )
)

lazy val tests: Project = (project in file("tests")).
  settings(buildSettings ++ Seq(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test,
    libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.12"
  )
) dependsOn macros
