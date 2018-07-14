//package trainmapper.networkrail
//
//import java.time.{Clock, LocalDate}
//
//import cats.data.OptionT
//import cats.effect.IO
//import com.typesafe.scalalogging.StrictLogging
//import fs2.{Pipe, Sink}
//import fs2.async.mutable.Queue
//import io.circe.Error
//import io.circe.parser.decode
//import stompa.Message
//import trainmapper.Shared.{JourneyDetails, MovementPacket, TOC, TrainId}
//import trainmapper.cache.Cache
//import trainmapper.schedule.ScheduleTable
//
//trait MovementMessageReceiver {
//
//  def handleIncomingMessages(inboundQueue: Queue[IO, stompa.Message],
//                             outboundQueue: Queue[IO, MovementPacket]): fs2.Stream[IO, Unit]
//}
//
//object MovementMessageReceiver extends StrictLogging {
//
//  def apply(cache: Cache[TrainId, TrainActivationMessage], scheduleTable: ScheduleTable)(clock: Clock) =
//    new MovementMessageReceiver {
//      override def handleIncomingMessages(inboundQueue: Queue[IO, Message],
//                                          outboundQueue: Queue[IO, MovementPacket]): fs2.Stream[IO, Unit] =
//        inboundQueue.dequeue
//          .through(messagesDecoder)
//          .collect { case Right(x) => x }
//          .flatMap { msgs =>
//            fs2.Stream.fromIterator[IO, IncomingMessage](msgs.toIterator).flatMap {
//              case msg: TrainMovementMessage =>
//                fs2.Stream
//                  .eval(IO(msg))
//                  .filter(_.toc == TOC("88"))
//                  .observe1(m => IO(logger.info(s"Received message $m")))
//                  .through(toMovementPacket)
//                  .observe1(m => IO(logger.info(s"Movement packet: $m")))
//                  .collect { case Some(packet) => packet }
//                  .to(outboundQueue.enqueue)
//              case msg: TrainActivationMessage =>
//                fs2.Stream
//                  .eval(IO(msg))
//                  .to(persistToCache)
//              case _ => fs2.Stream.empty
//            }
//          }
//
//      private def messagesDecoder: Pipe[IO, Message, Either[Error, List[IncomingMessage]]] =
//        (in: fs2.Stream[IO, Message]) => in.map(msg => logOnFailure(decode[List[IncomingMessage]](msg.body)))
//
//      private def toMovementPacket: Pipe[IO, TrainMovementMessage, Option[MovementPacket]] =
//        (in: fs2.Stream[IO, TrainMovementMessage]) =>
//          in.evalMap(msg => {
//            (for {
//              activationRec     <- OptionT(cache.get(msg.trainId))
//              stanoxCode        <- OptionT.fromOption[IO](msg.stanoxCode)
//              latLng            <- OptionT.fromOption[IO](Reference.latLngFor(stanoxCode))
//              originStationName <- OptionT.fromOption[IO](Reference.stationNameFor(activationRec.originStanox))
//              scheduleRecords <- OptionT.liftF(
//                scheduleTable
//                  .scheduleFor(activationRec.scheduleTrainId)
//                  .map(_.filter(rec =>
//                    rec.scheduleStart.compareTo(LocalDate.now(clock)) <= 0 && rec.scheduleEnd.compareTo(
//                      LocalDate.now(clock)) >= 0).sortBy(_.sequence)))
//            } yield {
//              MovementPacket(
//                msg.trainId,
//                activationRec.scheduleTrainId,
//                msg.trainServiceCode,
//                latLng,
//                msg.actualTimestamp,
//                JourneyDetails(originStationName, activationRec.originDepartureTimestamp),
//                scheduleRecords.map(_.toScheduleDetailsRecord)
//              )
//            }).value
//          })
//
//      private def persistToCache: Sink[IO, TrainActivationMessage] = _.evalMap(msg => cache.put(msg.trainId, msg)(None))
//
//      private def logOnFailure[A](e: Either[Error, A]): Either[Error, A] =
//        e match {
//          case Left(err) =>
//            logger.error("Decoding failure", err)
//            e
//          case _ => e
//
//        }
//    }
//}
