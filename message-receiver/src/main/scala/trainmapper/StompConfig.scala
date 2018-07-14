package trainmapper

import com.typesafe.config.ConfigFactory
import stompa.{StompConfig => StompaConfig}

object StompConfig {
  def read = {
    val config = ConfigFactory.load()
    StompaConfig(
      host = config.getString("stomp.host"),
      port = config.getInt("stomp.port"),
      username = config.getString("stomp.username"),
      password = config.getString("stomp.password")
    )
  }
}
