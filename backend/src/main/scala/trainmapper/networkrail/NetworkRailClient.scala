package trainmapper.networkrail

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import stompa.{Handler, StompClient, Topic}


trait NetworkRailClient {
  def subscribeToTopic(topic: Topic, client: StompClient[IO], handler: Handler[IO]): IO[Unit]
}

object NetworkRailClient extends StrictLogging {

  def apply() = new NetworkRailClient {
    override def subscribeToTopic(topic: Topic, client: StompClient[IO], handler: Handler[IO]): IO[Unit] =
      for {
        _ <- IO(logger.info(s"Subscribing to $topic"))
        _ <- client.subscribe(topic, handler)
      } yield ()
  }

}
