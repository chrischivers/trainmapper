package trainmapper.db

import java.time.{LocalDate, LocalTime}

import cats.effect.IO
import cats.syntax.functor._
import doobie.Transactor
import doobie._
import doobie.implicits._
import doobie.util.meta.Meta
import io.circe.Decoder
import trainmapper.Shared._
import trainmapper.db.PolylineTable.{PolylineRecord, ScheduleRecord}

trait PolylineTable extends Table[PolylineRecord] {
  def polylineFor(fromTipLoc: TipLocCode, toTipLoc: TipLocCode): IO[Option[Polyline]]
  def polylineIdFor(fromTipLoc: TipLocCode, toTipLoc: TipLocCode): IO[Option[Int]]
  def polyLineFor(id: Int): IO[Option[Polyline]]
  def insertAndRetrieveInsertedId(fromTiploc: TipLocCode, toTiploc: TipLocCode, polyline: Polyline): IO[Int]
  def deleteAllRecords: IO[Unit]
}

object PolylineTable {

  case class PolylineRecord(id: Int, fromTiplocCode: TipLocCode, toTiplocCode: TipLocCode, polyline: Polyline)
  object ScheduleRecord {

    import io.circe.generic.semiauto._

    implicit val tipLocCodeMeta: Meta[TipLocCode] = Meta[String].xmap(TipLocCode(_), _.value)
    implicit val polylineMeta: Meta[Polyline]     = Meta[String].xmap(Polyline(_), _.value)

    implicit val decoder: Decoder[PolylineRecord] = deriveDecoder
  }

  def apply(db: Transactor[IO]) = new PolylineTable {

    import ScheduleRecord._

    private def insertFragment(fromTiploc: TipLocCode, toTiploc: TipLocCode, polyline: Polyline) = fr"""
      INSERT INTO polylines
      (from_tiploc_code, to_tiploc_code, polyline)
      VALUES($fromTiploc, $toTiploc, $polyline)
     """

    override protected def insertRecord(record: PolylineRecord): IO[Unit] =
      insertFragment(record.fromTiplocCode, record.toTiplocCode, record.polyline).update.run
        .transact(db)
        .void

    override def polylineFor(fromTipLoc: TipLocCode, toTipLoc: TipLocCode): IO[Option[Polyline]] =
      sql"""
      SELECT polyline
      FROM polylines
      WHERE from_tiploc_code = ${fromTipLoc}
      AND to_tiploc_code = ${toTipLoc}
      """
        .query[Polyline]
        .option
        .transact(db)

    override def polylineIdFor(fromTipLoc: TipLocCode, toTipLoc: TipLocCode): IO[Option[Int]] =
      sql"""
      SELECT id
      FROM polylines
      WHERE from_tiploc_code = ${fromTipLoc}
      AND to_tiploc_code = ${toTipLoc}
      """
        .query[Int]
        .option
        .transact(db)

    override def insertAndRetrieveInsertedId(fromTiploc: TipLocCode,
                                             toTiploc: TipLocCode,
                                             polyline: Polyline): IO[Int] = {
      logger.info(s"Inserting polyline from $fromTiploc to $toTiploc")
      insertFragment(fromTiploc, toTiploc, polyline).update
        .withUniqueGeneratedKeys[Int]("id")
        .transact(db)
    }

    override def polyLineFor(id: Int): IO[Option[Polyline]] =
      sql"""
      SELECT polyline
      FROM polylines
      WHERE id = ${id}
      """
        .query[Polyline]
        .option
        .transact(db)

    override def deleteAllRecords: IO[Unit] = sql"""DELETE FROM polylines""".update.run.transact(db).void
  }
}
