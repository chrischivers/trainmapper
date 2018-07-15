package trainmapper

import com.itv.bucky.{AmqpClientConfig, ExchangeName, QueueName, RoutingKey}
import com.itv.bucky.decl._
import com.itv.bucky.pattern.requeue.requeueDeclarations
import com.typesafe.config.ConfigFactory

object RabbitConfig {

  val activationRoutingKey = RoutingKey("0001")
  val activationQueue      = Queue(QueueName("train-movements.activation"))
  val requeueDeclaration   = requeueDeclarations(activationQueue.name, activationRoutingKey)
  val trainMovementsExchange = Exchange(ExchangeName("train-movements"), exchangeType = Topic)
    .binding(activationRoutingKey -> activationQueue.name)

  val declarations = List[Declaration](activationQueue, trainMovementsExchange) ++ requeueDeclaration

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
