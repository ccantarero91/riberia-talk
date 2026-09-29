ThisBuild / scalaVersion := "3.3.8" // LTS

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect"         % "3.7.1",
    "org.http4s"    %% "http4s-ember-client" % "0.23.38",
    "org.http4s"    %% "http4s-circe"        % "0.23.38",
    "io.circe"      %% "circe-core"          % "0.14.16",
    "org.tpolecat"  %% "doobie-core"         % "1.0.0-RC13",
    "org.tpolecat"  %% "doobie-postgres"     % "1.0.0-RC13"
  )
)

lazy val `riberia-talk` = project
  .in(file("."))
  .aggregate(code, talk)

lazy val code = project
  .in(file("modules/code"))
  .settings(commonSettings)

lazy val talk =
  project
    .in(file("modules/talk"))
    .enablePlugins(MdocPlugin, GitHubPagesPlugin)
    .settings(
      gitHubPagesOrgName  := "ccantarero91",
      gitHubPagesRepoName := "riberia-talk",
      gitHubPagesSiteDir  := baseDirectory.value / "target" / "mdoc",
      mdocIn              := baseDirectory.value / "slides"
    )
    .settings(commonSettings)
    .dependsOn(code)
