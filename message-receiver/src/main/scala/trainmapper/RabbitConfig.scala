package trainmapper

import com.itv.bucky.{AmqpClientConfig, ExchangeName}
import com.itv.bucky.decl._
import com.typesafe.config.ConfigFactory

object RabbitConfig {

  val trainMovementsExchange = Exchange(ExchangeName("train-movements"), exchangeType = Topic)

  def read = {
    val config = ConfigFactory.load()
    AmqpClientConfig(
      host = config.getString("rabbit.host"),
      port = config.getInt("rabbit.port"),
      username = config.getString("rabbit.username"),
      password = config.getString("rabbit.password")
    )
  }
}
