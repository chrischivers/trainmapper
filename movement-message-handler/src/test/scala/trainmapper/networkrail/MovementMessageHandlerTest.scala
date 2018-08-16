package trainmapper.networkrail

import cats.effect.IO
import com.itv.bucky._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import trainmapper.Shared
import trainmapper.Shared._
import trainmapper.db.ScheduleTable.ScheduleRecord
import trainmapper.networkrail.TestData._

class MovementMessageHandlerTest extends FlatSpec with TestFixture {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val monadError = future.futureMonad

  "Movement message handler" should "decode incoming movement message and push to list cache" in {

    withApp() { app =>
      for {
        _         <- app.publishIncoming(TestData.createIncomingMovementMessageJson())
        _         <- app.waitForMessagesToBeProcessed
        cacheSize <- app.cacheSize
        fromCache <- app.getFromCache(TestData.defaultTrainId)
      } yield {

        cacheSize shouldBe 1

        fromCache should ===(List(TestData.defaultMovementPacket))
      }
    }
  }

  it should "decode multiple incoming movement with the same train id messages and push to list" in {
    val incomingMessage1 = TestData.createIncomingMovementMessageJson()
    val stanoxCode2      = StanoxCode("85515")
    val timestamp2       = TestData.defaultMovementTimestamp + 60000

    val incomingMessage2 = TestData.createIncomingMovementMessageJson(stanoxCode = stanoxCode2,
                                                                      actualTimestamp = timestamp2,
                                                                      eventType = EventType.Departure,
                                                                      plannedTimestamp = timestamp2,
                                                                      plannedPassengerTimestamp = timestamp2)

    val stopReferenceDetails1 = TestData.defaultStopReferenceDetails
    val stopReferenceDetails2 =
      StopReferenceDetailsWithLatLng("Description1",
                                     Some(CRS("CRS2")),
                                     Some(TipLocCode("FALMTHT")),
                                     Some(stanoxCode2),
                                     Some(LatLng(50.14833146498763, -5.0648652929940035)))

    val scheduleRecord2 = ScheduleRecord(
      None,
      defaultScheduleTrainId,
      1,
      defaultServiceCode,
      TipLocCode("FALMTHT"),
      LocationType.IntermediateLocation,
      Some(defaultMovementDateTime.toLocalTime.plusMinutes(4)),
      Some(defaultMovementDateTime.toLocalTime.plusMinutes(5)),
      DaysRun("1111100"),
      defaultMovementDateTime.toLocalDate,
      defaultMovementDateTime.toLocalDate.plusDays(1),
      Some(2)
    )

    withApp(stopReferenceDetails = List(stopReferenceDetails1, stopReferenceDetails2),
            scheduleRecords = List(TestData.defaultScheduleRecord, scheduleRecord2)) { app =>
      for {
        _         <- app.publishIncoming(incomingMessage1)
        _         <- app.publishIncoming(incomingMessage2)
        _         <- app.waitForMessagesToBeProcessed
        cacheSize <- app.cacheSize
        fromCache <- app.getFromCache(TestData.defaultTrainId)

      } yield {

        println("timestamp2: " + timestamp2)

        cacheSize shouldBe 1
        fromCache should have size 2
        val expectedRecord1 = TestData.defaultMovementPacket
        val expectedRecord2 = expectedRecord1.copy(
          stanoxCode = Some(stanoxCode2),
          actualTime = Shared.MovementPacket.timeStampToString(timestamp2),
          actualTimeStamp = timestamp2,
          plannedTime = Some(MovementPacket.timeStampToString(timestamp2)),
          plannedTimestamp = Some(timestamp2),
          plannedPassengerTime = Some(MovementPacket.timeStampToString(timestamp2)),
          plannedPassengerTimestamp = Some(timestamp2),
          eventType = EventType.Departure,
          stopReferenceDetails = Some(stopReferenceDetails2),
          scheduleToNextStop = Some(scheduleRecord2.toScheduleDetailsRecord(None))
        )
        fromCache should ===(List(expectedRecord1, expectedRecord2).reverse)
      }

    }
  }

  it should "expire movement message list after a set period of time" in {
    import scala.concurrent.duration._
    val cacheExpiry = 1.second

    withApp(applicationConfig = defaultApplicationConfig.copy(movementExpiry = Some(cacheExpiry))) { app =>
      for {
        _          <- app.publishIncoming(TestData.createIncomingMovementMessageJson())
        _          <- app.waitForMessagesToBeProcessed
        cacheSize  <- app.cacheSize
        fromCache1 <- app.getFromCache(TestData.defaultTrainId)
        _          <- IO(cacheSize shouldBe 1)
        _          <- IO(fromCache1 should ===(List(TestData.defaultMovementPacket)))
        _          <- IO.sleep(cacheExpiry.plus(10.millisecond))
        fromCache2 <- app.getFromCache(TestData.defaultTrainId)
      } yield {
        fromCache2 shouldBe empty
      }
    }
  }

}
