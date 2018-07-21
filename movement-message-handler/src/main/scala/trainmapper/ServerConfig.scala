package trainmapper

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration

object ServerConfig {

  case class ApplicationConfig(port: Int, googleMapsApiKey: String, movementExpiry: FiniteDuration)

  def read: ApplicationConfig = {
    val config = ConfigFactory.load()
    ApplicationConfig(
      config.getInt("server.port"),
      config.getString("server.mapsApiKey"),
      FiniteDuration(config.getDuration("movementExpiry").toMillis, TimeUnit.MILLISECONDS)
    )
  }

}
