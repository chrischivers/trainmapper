package trainmapper

import com.typesafe.config.ConfigFactory

object ServerConfig {
  case class ServerConfig(port: Int)

  def read: ServerConfig = {
    val config = ConfigFactory.load()
    ServerConfig(config.getInt("port"))
  }
}
