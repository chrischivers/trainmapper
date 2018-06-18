package trainmapper

import com.typesafe.config.{ConfigFactory, Config => TypesafeConfig}
import stompa.{StompConfig, Topic}

object Config {

  case class TrainMapperConfig(movementTopic: Topic, googleMapsApiKey: String, stompConfig: StompConfig)

  def apply(config: TypesafeConfig = ConfigFactory.load()) =
    TrainMapperConfig(
      movementTopic = Topic(config.getString("networkRail.movement-topic")),
      googleMapsApiKey = config.getString("google-maps-api-key"),
      stompConfig = StompConfig(
        config.getString("networkRail.feed.host"),
        config.getInt("networkRail.feed.port"),
        config.getString("networkRail.feed.username"),
        config.getString("networkRail.feed.password")
      )
    )

}
