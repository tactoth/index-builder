
name := """index-builder"""

version := "1.0"

lazy val root = project in file(".")

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.beust" % "jcommander" % "1.30"
)

libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.1.7"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"


