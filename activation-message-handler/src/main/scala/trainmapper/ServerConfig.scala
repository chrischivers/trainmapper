package trainmapper

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration

object ServerConfig {
  case class ApplicationConfig(port: Int, redisExpiry: FiniteDuration)

  def read: ApplicationConfig = {
    val config = ConfigFactory.load()
    ApplicationConfig(config.getInt("port"),
                      FiniteDuration(config.getDuration("activationExpiry").toMillis, TimeUnit.MILLISECONDS))
  }
}
