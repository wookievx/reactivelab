name := "reactive-lab01"

version := "1.0"

scalaVersion := "2.12.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka"           %% "akka-http"        % "10.0.10",
  "com.typesafe.akka"           %% "akka-actor"       % "2.5.6",
  "com.typesafe.akka"           %% "akka-testkit"     % "2.5.6" % Test,
  "org.scalatest"               %% "scalatest"        % "3.0.4" % "test",
  "com.typesafe.akka"           %% "akka-persistence" % "2.5.6",
  "org.iq80.leveldb"             % "leveldb"          % "0.9",
  "org.fusesource.leveldbjni"    % "leveldbjni-all"   % "1.8",
  "com.typesafe.akka"           %% "akka-remote"      % "2.5.6"
)
        