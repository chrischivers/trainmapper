package trainmapper.server

import cats.effect.{Effect, IO}
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import fs2.async.mutable.Queue
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeBuilder
import org.http4s
import trainmapper.ServerConfig
import trainmapper.Shared.MovementPacket

import scala.concurrent.ExecutionContext

abstract class Server[F[_]: Effect](implicit executionContext: ExecutionContext) extends Http4sDsl[F] {

  val config = ServerConfig.read

  type PrefixAndService = (String, http4s.HttpService[F])
  def httpServices: List[PrefixAndService]

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] =
    for {
      exitCode <- BlazeBuilder[F]
        .bindHttp(config.port)
        .withWebSockets(true)
        .mountService(Router(httpServices.map(x => (x._1, x._2)): _*))
        .serve
    } yield exitCode
}

object ServerWithWebsockets {
  def apply[F[_]](queue: Queue[F, MovementPacket], googleMapsApiKey: String)(implicit effect: Effect[F],
                                                                             executionContext: ExecutionContext) =
    new Server[F] {

      override def httpServices: List[(String, http4s.HttpService[F])] =
        List(("/", MapService[F](googleMapsApiKey)), ("/ws", WSService(queue)))

    }
}

object Server extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    new Server[IO] {
      override def httpServices: List[(String, http4s.HttpService[IO])] =
        List(("/", MapService[IO](config.googleMapsApiKey)))
    }.stream(args, requestShutdown)
}
