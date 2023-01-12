import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.streaming.kinesis.{KinesisInitialPositions, KinesisInputDStream}
import org.apache.spark.storage.StorageLevel
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.log4j.Logger
import org.apache.log4j.Level

object SparkKinesisMain {


  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.OFF)
    val streamName = "spark-kinesis"
    val endpointUrl = "https://kinesis.us-east-1.amazonaws.com"
    val regionName = "us-east-1"
    val appName = "transactions"
    val batchInterval = Milliseconds(5000)

    val spark = SparkSession
      .builder()
      .appName(appName)
      .config("spark.databricks.hive.metastore.glueCatalog.enabled", "true")
      .enableHiveSupport()
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.sql.sources.partitionOverwriteMode", "dynamic")
      .config("spark.sql.parquet.writeLegacyFormat", "true")
      .config("spark.kryo.unsafe", "true")
      .config("spark.rdd.compress", "true")
      .config("spark.sql.adaptive.enabled", "true")
      .config("hive.optimize.s3.query", "true")
      .config("hive.exec.parallel", "true")
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .config("spark.sql.parquet.cacheMetadata", "false")
      .getOrCreate()
    spark.sparkContext.hadoopConfiguration.set("fs.s3.acl.default", "BucketOwnerFullControl")

    spark.catalog.setCurrentDatabase("transactional")
    val customerDF = spark.table("customer").cache()
    val countriesDF = spark.table("country").cache()
    val storeDF = spark.table("store").cache()
    val table = "transaction"

    val ssc = new StreamingContext(spark.sparkContext, batchInterval)


    val kinesisStream = KinesisInputDStream.builder
      .streamingContext(ssc)
      .streamName(streamName)
      .endpointUrl(endpointUrl)
      .regionName(regionName)
      .initialPosition(new KinesisInitialPositions.Latest())
      .checkpointAppName(appName) //    application used in dynamoDB
      .checkpointInterval(batchInterval) // interval at which the DynamoDB is updated with information
      .storageLevel(StorageLevel.MEMORY_AND_DISK_2)
      .build()

    val transactions = kinesisStream.map { byteArray =>
      val Array(id, customerId, countryId, storeId, purchaseAmount, creditCard, timeStamp) = new String(byteArray).split(",")
      Transaction(id, customerId, countryId, storeId, purchaseAmount.toFloat, creditCard, timeStamp)
    }

    transactions.foreachRDD { rdd =>

      val transactionDF = spark.createDataFrame(rdd)
      transactionDF.show()
      val transactionCustomerDF = transactionDF.join(customerDF, "customerId")
      val transactionCountriesDF = transactionCustomerDF.join(countriesDF, ("countryId"))
      val finalDF = transactionCountriesDF.join(storeDF, "storeId")
      finalDF.show(false)

      if (finalDF.count() > 0) {

        if (!spark.catalog.tableExists(table)) {
          finalDF
            .write
            .format("parquet")
            .mode(SaveMode.Overwrite)
            .saveAsTable(s"transactional.$table")

        } else {
          finalDF
            .write
            .format("parquet")
            .mode(SaveMode.Append)
            .insertInto(s"transactional.$table")
        }
      }
    }

    ssc.start()
    ssc.awaitTermination()

  }

  case class Transaction(id: String,
                         customerId: String,
                         countryId: String,
                         storeId: String,
                         purchaseAmount: Float,
                         creditCard: String,
                         timeStamp: String)

}
