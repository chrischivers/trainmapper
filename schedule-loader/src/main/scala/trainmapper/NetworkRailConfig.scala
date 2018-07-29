package trainmapper

import com.typesafe.config.ConfigFactory
import org.http4s.Uri

case class NetworkRailConfig(username: String, password: String, scheduleUrl: Uri)

object NetworkRailConfig {

  def read: NetworkRailConfig = {
    val config = ConfigFactory.load()
    NetworkRailConfig(
      config.getString("networkRail.username"),
      config.getString("networkRail.password"),
      Uri.unsafeFromString(config.getString("networkRail.schedule-url"))
    )
  }
}
