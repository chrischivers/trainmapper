package trainmapper.networkrail

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import fs2.{Pipe, Sink}
import fs2.async.mutable.Queue
import io.circe.Error
import io.circe.parser.decode
import stompa.Message
import trainmapper.Shared.{MovementPacket, TrainId}
import trainmapper.cache.Cache

trait MovementMessageHandler {

  def handleIncomingMessages(inboundQueue: Queue[IO, stompa.Message],
                             outboundQueue: Queue[IO, MovementPacket]): fs2.Stream[IO, Unit]
}

object MovementMessageHandler extends StrictLogging {

  def apply(cache: Cache[TrainId, TrainActivationMessage]) = new MovementMessageHandler {
    override def handleIncomingMessages(inboundQueue: Queue[IO, Message],
                                        outboundQueue: Queue[IO, MovementPacket]): fs2.Stream[IO, Unit] =
      inboundQueue.dequeue
        .through(messageDecoder)
        .collect { case Right(x) => x }
        .flatMap { msgs =>
          fs2.Stream.fromIterator[IO, IncomingMessage](msgs.toIterator).flatMap {
            case msg: TrainMovementMessage =>
              fs2.Stream
                .eval(IO(msg))
                .through(toMovementPacket)
                .collect { case Some(packet) => packet }
                .to(outboundQueue.enqueue)
            case msg: TrainActivationMessage =>
              fs2.Stream
                .eval(IO(msg))
                .to(persistToCache)
            case _ => fs2.Stream.empty
          }
        }

    private def messageDecoder: Pipe[IO, Message, Either[Error, List[IncomingMessage]]] =
      (in: fs2.Stream[IO, Message]) => in.map(msg => logOnFailure(decode[List[IncomingMessage]](msg.body)))

    private def toMovementPacket: Pipe[IO, TrainMovementMessage, Option[MovementPacket]] =
      (in: fs2.Stream[IO, TrainMovementMessage]) =>
        in.map(
          msg =>
            msg.stanoxCode
              .flatMap(Reference.latLngFor)
              .map(latLng => MovementPacket(msg.trainId, msg.trainServiceCode, latLng, msg.actualTimestamp)))

    private def persistToCache: Sink[IO, TrainActivationMessage] = _.evalMap(msg => cache.put(msg.trainId, msg)(None))

    private def logOnFailure[A](e: Either[Error, A]): Either[Error, A] =
      e match {
        case Left(err) =>
          logger.error("Decoding failure", err)
          e
        case _ => e

      }
  }
}
