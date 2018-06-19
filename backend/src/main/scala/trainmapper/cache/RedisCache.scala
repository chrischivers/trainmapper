package trainmapper.cache

import akka.util.ByteString
import cats.effect.IO
import cats.syntax.functor._
import io.circe.parser.parse
import redis.{ByteStringDeserializer, ByteStringSerializer, RedisClient}
import trainmapper.Shared.TrainId
import trainmapper.networkrail.TrainActivationMessage
import io.circe.syntax._

import scala.concurrent.duration.FiniteDuration

object RedisCache {

  implicit val byteStringSerializer = new ByteStringSerializer[TrainActivationMessage] {
    override def serialize(data: TrainActivationMessage): ByteString =
      ByteString(data.asJson.noSpaces)
  }

  implicit val byteStringDeserializer = new ByteStringDeserializer[TrainActivationMessage] {
    override def deserialize(bs: ByteString): TrainActivationMessage =
      (for {
        json    <- parse(bs.utf8String)
        decoded <- json.as[TrainActivationMessage]
      } yield decoded).fold(err => throw err, identity)

  }

  def apply(redisClient: RedisClient) = new Cache[TrainId, TrainActivationMessage] {

    override def put(key: TrainId, rec: TrainActivationMessage)(expiry: Option[FiniteDuration]): IO[Unit] =
      IO.fromFuture(
          IO(redisClient
            .set(key.value, rec, pxMilliseconds = expiry.map(_.toMillis))))
        .void

    override def get(key: TrainId): IO[Option[TrainActivationMessage]] =
      IO.fromFuture(IO {
        redisClient.get[TrainActivationMessage](key.value)
      })

  }
}
