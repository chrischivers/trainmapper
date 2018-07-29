package trainmapper.clients

import cats.effect.IO
import cats.instances.list._
import cats.syntax.traverse._
import com.typesafe.scalalogging.StrictLogging
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import trainmapper.Shared.{CRS, StanoxCode, StopReferenceDetails, TipLocCode}

trait RailwaysCodesClient {

  def parseAllCodes(): IO[List[StopReferenceDetails]]

  def parseCodesFor(startingLetter: Char): IO[List[StopReferenceDetails]]
}

object RailwaysCodesClient extends StrictLogging {

  def apply() = new RailwaysCodesClient {
    override def parseCodesFor(startingLetter: Char): IO[List[StopReferenceDetails]] = IO {
      logger.info(s"parsing railway codes for letter $startingLetter")
      val browser = JsoupBrowser()
      val url     = s"http://www.railwaycodes.org.uk/crs/CRS${startingLetter.toLower}.shtm"
      logger.info(s"Using url $url")
      val doc = browser.get(url)

      doc.body.select("table").toList(1).select("tr").toList.flatMap { row =>
        val columns = row.select("td").toList
        if (columns.size == 6) {
          Some(StopReferenceDetails(
            columns(0).text,
            emptyStringToNone(columns(1).text).map(CRS(_)),
            emptyStringToNone(columns(3).text).map(TipLocCode(_)),
            emptyStringToNone(columns(5).text).map(StanoxCode(_))
          ))
        } else None
      }
    }

    override def parseAllCodes(): IO[List[StopReferenceDetails]] =
      ('a' to 'z').toList.flatTraverse[IO, StopReferenceDetails] { char =>
        parseCodesFor(char)

      }
  }
  private def emptyStringToNone(str: String): Option[String] = if (str.trim.isEmpty) None else Some(str)
}
