package app.config

data class Config(
  val serverName: String,
  val port: Int,
  val workerNum: Int,
  val selectorNum: Int,
)
