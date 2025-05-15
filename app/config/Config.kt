package app.config

data class Config(
  val appName: String,
  val port: Int,
  val workerNum: Int,
  val selectorNum: Int,
  val initialCapacity: Int,
  val loadFactor: Float,
  val minBlockSize: Int,
  val maxBlockSize: Int
)
