package trainmapper

import akka.actor.ActorSystem
import akka.util.ByteString
import cats.effect.IO
import fs2.async.Ref
import redis.{ByteStringDeserializer, ByteStringSerializer, RedisClient}

import scala.concurrent.Future

object StubRedisClient {

  implicit val actorSystem = ActorSystem()

  def apply(ref: Ref[IO, Map[String, ByteString]]) =
    new RedisClient() {

      override def get[R](key: String)(implicit byteStringDeserializer: ByteStringDeserializer[R]): Future[Option[R]] =
        ref.get
          .map(m => m.get(key))
          .map(v => v.map(s => byteStringDeserializer.deserialize(s)))
          .unsafeToFuture()

      override def set[V](key: String,
                          value: V,
                          exSeconds: Option[Long],
                          pxMilliseconds: Option[Long],
                          NX: Boolean,
                          XX: Boolean)(implicit byteStringSerializer: ByteStringSerializer[V]): Future[Boolean] =
        ref.modify(m => m + (key -> byteStringSerializer.serialize(value))).map(_ => true).unsafeToFuture()
    }
}
