scalaVersion := "2.11.7"

organization := "com.github.dmexe"

name := "finagle-consul"

version := "0.0.1"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

resolvers += "twttr" at "http://maven.twttr.com/"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies ++= Seq(
  "commons-codec"  %  "commons-codec"  % "1.9",
  "com.twitter"    %% "finagle-core"   % "6.31.0",
  "com.twitter"    %% "finagle-http"   % "6.31.0",
  "org.json4s"     %% "json4s-jackson" % "3.2.11",
  "org.scalatest"  %% "scalatest"      % "2.2.4"   % "test"
)
