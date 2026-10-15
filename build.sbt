ThisBuild / scalaVersion := "3.3.8" // LTS

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.typelevel"       %% "cats-effect"          % "3.7.1",
    "io.circe"            %% "circe-core"           % "0.14.16",
    "io.circe"            %% "circe-parser"         % "0.14.16",
    "org.typelevel"       %% "doobie-core"          % "1.0.0-RC13",
    "org.typelevel"       %% "doobie-postgres"      % "1.0.0-RC13",
    "dev.langchain4j"      % "langchain4j"          % "1.22.0",
    "dev.langchain4j"      % "langchain4j-ollama"   % "1.22.0",
    "dev.langchain4j"      % "langchain4j-open-ai"  % "1.22.0",
    "com.sksamuel.scrimage" % "scrimage-core"       % "4.6.8"
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
