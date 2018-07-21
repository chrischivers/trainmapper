package trainmapper.http

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import org.http4s.circe.jsonEncoderOf
import org.http4s.dsl.io._
import org.http4s.{HttpService, QueryParamDecoder}
import trainmapper.Shared.{MovementPacket, TrainId}

import scala.concurrent.ExecutionContext

object MovementsHttp extends StrictLogging {

  implicit val entityEncoder                             = jsonEncoderOf[IO, MovementPacket]
  implicit val statusDecoder: QueryParamDecoder[TrainId] = QueryParamDecoder[String].map(TrainId(_))

  def apply()(implicit executionContext: ExecutionContext): HttpService[IO] =
    HttpService[IO] {
      case GET -> Root => Ok()

    }

}
