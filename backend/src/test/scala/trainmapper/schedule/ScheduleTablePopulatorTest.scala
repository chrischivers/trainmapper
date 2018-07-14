//package trainmapper.schedule
//
//import cats.effect.IO
//import org.http4s.client.Client
//import org.http4s.dsl.io._
//import org.http4s.{HttpService, StaticFile, Uri}
//import org.scalatest.FlatSpec
//import stompa.Topic
//import trainmapper.Config.NetworkRailConfig
//import trainmapper.TestFixture
//import org.scalatest.Matchers._
//
//class ScheduleTablePopulatorTest extends FlatSpec with TestFixture {
//
//  "Schedule Table Populator" should "populate the schedule table" in {
//    withDatabase() { db =>
//      val mockHttpService = HttpService[IO] {
//        case request @ GET -> Root =>
//          StaticFile.fromResource("/sample-toc-schedule-test.gz", Some(request)).getOrElseF(NotFound())
//
//      }
//      val scheduleTable = ScheduleTable(db)
//      val config        = NetworkRailConfig("", "", Uri.unsafeFromString("/"), 0, Topic(""), Uri.unsafeFromString(""))
//      val populator     = ScheduleTablePopulator(Client.fromHttpService(mockHttpService), scheduleTable, config)
//      for {
//        _ <- populator.populateTable()
//        x <- scheduleTable.allRecords
//      } yield {
//        x should have size 7079
//      }
//    }
//  }
//}
