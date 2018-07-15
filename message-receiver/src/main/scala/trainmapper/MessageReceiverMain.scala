package trainmapper

import cats.effect.IO
import com.itv.bucky.{fs2 => rabbitfs2}
import com.typesafe.scalalogging.StrictLogging
import stompa.{Message, StompClient, Topic}
import stompa.fs2.Fs2MessageHandler
import trainmapper.networkrail.MovementMessageReceiver

object MessageReceiverMain extends App with StrictLogging {
  import scala.concurrent.ExecutionContext.Implicits.global
  import stompa.support.IOSupport._

  val app = for {
    rabbitConfig        <- fs2.Stream.eval(IO(RabbitConfig.read))
    stompConfig         <- fs2.Stream.eval(IO(StompConfig.read))
    rabbitClient        <- rabbitfs2.clientFrom(rabbitConfig, RabbitConfig.declarations)
    inboundMessageQueue <- fs2.Stream.eval(fs2.async.mutable.Queue.unbounded[IO, Message])
    stompHandler        <- fs2.Stream.eval(IO(Fs2MessageHandler[IO](inboundMessageQueue)))
    stompClient         <- fs2.Stream.eval(IO(StompClient[IO](stompConfig)))
    _                   <- fs2.Stream.eval(stompClient.subscribe(Topic("/topic/TRAIN_MVT_ALL_TOC"), stompHandler))
    messageReceiver     <- fs2.Stream.eval(IO(MovementMessageReceiver(rabbitClient.publisher())))
    _                   <- messageReceiver.handleIncomingMessages(inboundMessageQueue)
  } yield ()

  app.compile.drain.unsafeRunSync()
}
