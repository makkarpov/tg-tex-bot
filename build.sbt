name := """tg-tex-bot"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  cache, ws,

  "org.scilab.forge" % "jlatexmath" % "1.0.4",
  "info.mukel" %% "telegrambot4s" % "2.2.1-SNAPSHOT",

  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)

