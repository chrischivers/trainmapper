package trainmapper.networkrail

import cats.effect.IO
import cats.syntax.functor._
import com.itv.bucky.CirceSupport.unmarshallerFromDecodeJson
import com.itv.bucky.UnmarshalResultOps._
import com.itv.bucky.decl.DeclarationExecutor
import com.itv.bucky.ext.fs2._
import com.itv.bucky.fs2._
import com.itv.bucky.{RoutingKey, future}
import fs2.async.mutable.Queue
import io.circe.Json
import io.circe.parser._
import org.scalatest.Matchers._
import org.scalatest.{Assertion, FlatSpec}
import stompa.Message
import trainmapper.RabbitConfig
import trainmapper.Shared._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MovementMessageReceiverTest extends FlatSpec {

  "Movement message handler" should "parse incoming message and put on rabbit queue with correct routing key" in evaluateStream {

    implicit val futureMonad = future.futureMonad

    val messageType1   = "0003"
    val messageType2   = "0004"
    val sampleMessage1 = sampleMessage(messageType1)
    val sampleMessage2 = sampleMessage(messageType2)
    val incomingMessage =
      Message(
        Map.empty,
        Json
          .arr(sampleMessage1, sampleMessage2)
          .noSpaces
      )

    for {
      inboundMessageQueue <- fs2.Stream.eval(fs2.async.mutable.Queue.unbounded[IO, Message])
      rabbitClient        <- fs2.Stream.eval(IO(rabbitSimulator))
      _                   <- fs2.Stream.eval(IO(DeclarationExecutor(RabbitConfig.declarations, rabbitClient)))
      consumer1           <- rabbitClient.consume(RabbitConfig.trainMovementsExchange.name, RoutingKey(messageType1))
      consumer2           <- rabbitClient.consume(RabbitConfig.trainMovementsExchange.name, RoutingKey(messageType2))
      messageReceiver     <- fs2.Stream.eval(IO(MovementMessageReceiver(rabbitClient.publisher())))
      _                   <- fs2.Stream.eval(inboundMessageQueue.enqueue1(incomingMessage))
      _                   <- timedRunMessageHandler(messageReceiver, inboundMessageQueue)
      _                   <- fs2.Stream.eval(rabbitClient.waitForMessagesToBeProcessed())
    } yield {
      consumer1.toList.map(_.body.unmarshal(unmarshallerFromDecodeJson[Json]).success) should ===(List(sampleMessage1))
      consumer2.toList.map(_.body.unmarshal(unmarshallerFromDecodeJson[Json]).success) should ===(List(sampleMessage2))
    }
  }

  def timedRunMessageHandler(messageReceiver: MovementMessageReceiver, inboundQueue: Queue[IO, Message]) =
    fs2.Stream.eval(
      IO(
        messageReceiver
          .handleIncomingMessages(inboundQueue)
          .compile
          .drain
          .unsafeRunTimed(1.second)).void)

  def evaluateStream[T](f: fs2.Stream[IO, Assertion]) = f.compile.drain.unsafeRunSync()

  def sampleMessage(messageType: String) = {
    val str =
      s"""
         |   {
         |      "header":{
         |         "msg_type":"$messageType",
         |         "source_dev_id":"",
         |         "user_id":"",
         |         "original_data_source":"TSIA",
         |         "msg_queue_timestamp":"1515939674000",
         |         "source_system_id":"TRUST"
         |      },
         |      "body":{
         |         "schedule_source":"C",
         |         "train_file_address":null,
         |         "schedule_end_date":"2018-01-14",
         |         "train_id":"382Y351718",
         |         "tp_origin_timestamp":"2018-01-14",
         |         "creation_timestamp":"1515939673000",
         |         "tp_origin_stanox":"",
         |         "origin_dep_timestamp":"${System.currentTimeMillis()}",
         |         "train_service_code":"38201",
         |         "toc_id":"88",
         |         "d1266_record_number":"00000",
         |         "train_call_type":"AUTOMATIC",
         |         "train_uid":"73642",
         |         "train_call_mode":"NORMAL",
         |         "schedule_type":"P",
         |         "sched_origin_stanox":"38201",
         |         "schedule_wtt_id":"2A47M",
         |         "schedule_start_date":"2018-01-14"
         |      }
         |   }
       """.stripMargin
    parse(str).right.get
  }
}
