package app.config

data class Config(
  val appName: String,
  val port: Int,
  val workerNum: Int,
  val selectorNum: Int,
  val minBlockSize: Int,
  val maxBlockSize: Int,
  val segmentSize: Int,
  val regularSize: Int,
  val slotSize: Int
)
