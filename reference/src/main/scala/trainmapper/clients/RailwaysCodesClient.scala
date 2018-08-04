package trainmapper.clients

import cats.effect.IO
import cats.instances.list._
import cats.syntax.traverse._
import com.typesafe.scalalogging.StrictLogging
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import trainmapper.Shared.{CRS, StanoxCode, StopReferenceDetails, TipLocCode}

import scala.collection.mutable

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

      type RowNumber  = Int
      type CellNumber = Int

      val holdingMap = mutable.Map[RowNumber, Map[CellNumber, String]]()

      doc.body.select("table").toList(1).select("tr").toList.zipWithIndex.flatMap {
        case (row, rowNumber) =>
          val cells = row.select("td").toList.zipWithIndex

          logger.debug(s"Parsing station: ${cells.head._1.text}")

          val cellNumberRowSpan = cells.collect {
            case (cell, cellNumber) if cell.hasAttr("rowspan") => (cellNumber, cell.attr("rowspan").toInt)
          }
          cellNumberRowSpan.foreach {
            case (cellNumber, rowSpan) =>
              (1 until rowSpan).foreach { i =>
                val existing = holdingMap.getOrElse(rowNumber + 1, Map.empty)
                holdingMap.update(rowNumber + i, existing + (cellNumber -> cells(cellNumber)._1.text))
              }
          }

          def checkHoldingMapOrRetrieve(cellNumber: CellNumber) = {
            val cellsHeldFoRow        = holdingMap.get(rowNumber)
            val textHeldForCellNumber = cellsHeldFoRow.flatMap(_.get(cellNumber))
            (cellsHeldFoRow, textHeldForCellNumber) match {
              case (None, None) => cells.lift(cellNumber).map(_._1.text)
              case (Some(cellsHeld), None) =>
                cells.lift(cellNumber - cellsHeld.keys.count(_ < cellNumber)).map(_._1.text)
              case (_, Some(textHeld)) => Some(textHeld)
            }
          }

          for {
            desc   <- checkHoldingMapOrRetrieve(0)
            crs    <- checkHoldingMapOrRetrieve(1).map(v => validateAndFormatResponseString(v).map(CRS(_)))
            tipLoc <- checkHoldingMapOrRetrieve(3).map(v => validateAndFormatResponseString(v).map(TipLocCode(_)))
            stanox <- checkHoldingMapOrRetrieve(5).map(v => validateAndFormatResponseString(v).map(StanoxCode(_)))

          } yield StopReferenceDetails(desc, crs, tipLoc, stanox)
      }
    }

    override def parseAllCodes(): IO[List[StopReferenceDetails]] =
      ('a' to 'z').toList.flatTraverse[IO, StopReferenceDetails] { char =>
        parseCodesFor(char)

      }
  }
  private def validateAndFormatResponseString(str: String): Option[String] =
    if (str.trim.isEmpty) None
    else Some(str.replace("*", "").replace("-", ""))
}
