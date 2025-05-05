package app.config

data class Config(
  val appName: String,
  val port: Int,
  val workerNum: Int,
  val selectorNum: Int,
)
