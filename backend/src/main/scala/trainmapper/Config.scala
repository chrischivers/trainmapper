package trainmapper

import com.typesafe.config.{ConfigFactory, Config => TypesafeConfig}
import org.http4s.Uri
import stompa.{StompConfig, Topic}

object Config {

  case class TrainMapperConfig(googleMapsApiKey: String,
                               networkRailConfig: NetworkRailConfig,
                               databaseConfig: DatabaseConfig)

  case class DatabaseConfig(driverClassName: String,
                            url: String,
                            username: String,
                            password: String,
                            maximumPoolSize: Int = 2)

  case class NetworkRailConfig(username: String,
                               password: String,
                               stompUrl: Uri,
                               stompPort: Int,
                               movementTopic: Topic,
                               scheduleUrl: Uri)

  def apply(config: TypesafeConfig = ConfigFactory.load()) =
    TrainMapperConfig(
      googleMapsApiKey = config.getString("google-maps-api-key"),
      networkRailConfig = NetworkRailConfig(
        config.getString("networkRail.username"),
        config.getString("networkRail.password"),
        Uri.unsafeFromString(config.getString("networkRail.host")),
        config.getInt("networkRail.port"),
        Topic(config.getString("networkRail.movement-topic")),
        Uri.unsafeFromString(config.getString("networkRail.schedule-url")),
      ),
      databaseConfig = DatabaseConfig(
        config.getString("db.driverClassName"),
        config.getString("db.url"),
        config.getString("db.username"),
        config.getString("db.password"),
        config.getInt("db.maximumPoolSize")
      )
    )

}
