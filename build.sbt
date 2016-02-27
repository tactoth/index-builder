
name := """index-builder"""

version := "1.0"

lazy val root = project in file(".")

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.beust" % "jcommander" % "1.30"
)

