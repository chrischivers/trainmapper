package trainmapper

import com.typesafe.config.{ConfigFactory, Config => TypesafeConfig}
import stompa.{StompConfig, Topic}

object Config {

  case class Config(movementTopic: Topic, stompConfig: StompConfig)

  def apply(config: TypesafeConfig = ConfigFactory.load()) =
    Config(
      movementTopic = Topic(config.getString("networkRail.movement-topic")),
      stompConfig = StompConfig(
        config.getString("networkRail.feed.host"),
        config.getInt("networkRail.feed.port"),
        config.getString("networkRail.feed.username"),
        config.getString("networkRail.feed.password")
      )
    )

}
