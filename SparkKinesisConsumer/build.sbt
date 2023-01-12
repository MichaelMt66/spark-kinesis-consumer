
lazy val root = (project in file(".")).
  settings(
    name := "SparkKinesisConsumer",
    version := "1.0",
    scalaVersion := "2.11.8",
    resourceDirectory in assembly := file("."),
    assemblyJarName in assembly := "SparkKinesisConsumer.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs@_*) => MergeStrategy.discard
      case x => MergeStrategy.first
    }
  )

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql" % "2.4.3" % "provided",
  "org.apache.spark" %% "spark-streaming-kinesis-asl" % "2.4.3",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.307",
  "com.amazonaws" % "aws-java-sdk-glue" % "1.11.307",
 "org.apache.hadoop" % "hadoop-aws" % "2.7.3" % "provided"

)