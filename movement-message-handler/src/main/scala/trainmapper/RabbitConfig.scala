package trainmapper

import com.itv.bucky.decl._
import com.itv.bucky.pattern.requeue.requeueDeclarations
import com.itv.bucky.{AmqpClientConfig, ExchangeName, QueueName, RoutingKey}
import com.typesafe.config.ConfigFactory

object RabbitConfig {

  val movementRoutingKey = RoutingKey("0003")
  val movementQueue      = Queue(QueueName("train-movements.movement"))
  val requeueDeclaration = requeueDeclarations(movementQueue.name, movementRoutingKey)
  val trainMovementsExchange = Exchange(ExchangeName("train-movements"), exchangeType = Topic)
    .binding(movementRoutingKey -> movementQueue.name)

  val declarations = List[Declaration](trainMovementsExchange) ++ requeueDeclaration

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
