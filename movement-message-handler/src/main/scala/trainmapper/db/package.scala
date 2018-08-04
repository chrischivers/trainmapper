package trainmapper

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import doobie.hikari.HikariTransactor
import fs2.Stream
import org.flywaydb.core.Flyway
import doobie.hikari.implicits._

package object db {

  private val migrationLocation = "db/migration"

  def setUpTransactor(config: DatabaseConfig)(beforeMigration: Flyway => Unit = _ => ()) =
    for {
      transactor <- HikariTransactor
        .newHikariTransactor[IO](config.driverClassName, config.url, config.username, config.password)
      _ <- transactor.configure { datasource =>
        IO {
          datasource.setMaximumPoolSize(config.maximumPoolSize)
          val flyway = new Flyway()
          flyway.setDataSource(datasource)
          flyway.setLocations(migrationLocation)
          beforeMigration(flyway)
          flyway.migrate()
        }
      }
    } yield transactor

  def withTransactor[A](config: DatabaseConfig)(beforeMigration: Flyway => Unit = _ => ())(
      f: HikariTransactor[IO] => Stream[IO, A]): Stream[IO, A] =
    Stream.bracket(setUpTransactor(config)(beforeMigration))(
      f,
      (t: HikariTransactor[IO]) => t.shutdown
    )

  trait Table[A] extends StrictLogging {

    protected def insertRecord(record: A): IO[Unit]

    def safeInsertRecord(record: A): IO[Unit] =
      insertRecord(record).attempt
        .map {
          case Right(_) => ()
          case Left(err) =>
            logger.error(s"Error inserting record to DB $record.", err)
        }
  }
}
