package trainmapper

import akka.actor.ActorSystem
import akka.util.ByteString
import cats.effect.IO
import fs2.async.Ref
import redis.{ByteStringDeserializer, ByteStringSerializer, RedisClient}

import scala.concurrent.Future
import scala.util.Try

object StubRedisListClient {

  implicit val actorSystem = ActorSystem()

  type ByteStringListAndExpiry = (List[ByteString], Long)

  def apply(ref: Ref[IO, Map[String, ByteStringListAndExpiry]]) =
    new RedisClient() {

      override def lpush[V](key: String, values: V*)(
          implicit byteStringSerializer: ByteStringSerializer[V]): Future[Long] =
        ref
          .modify(
            oldMap =>
              oldMap
                .get(key)
                .fold(oldMap + (key -> (values.toList.map(byteStringSerializer.serialize), Long.MaxValue)))(oldList =>
                  oldMap + (key     -> (values.toList.map(byteStringSerializer.serialize) ++ oldList._1, oldList._2))))
          .flatMap(_ => {
            ref.get.map(_.get(key).fold(values.size.toLong)(x => x._1.size) + values.size)
          })
          .unsafeToFuture()

      override def lrange[R](key: String, start: Long, stop: Long)(
          implicit byteStringDeserializer: ByteStringDeserializer[R]): Future[Seq[R]] =
        ref.get
          .map {
            _.get(key).fold(Seq.empty[R]) {
              case (list, expiry) =>
                if (expiry >= System.currentTimeMillis()) {
                  list
                    .slice(start.toInt, if (stop.toInt == -1) Int.MaxValue else stop.toInt)
                    .map(byteStringDeserializer.deserialize)
                } else Seq.empty[R]
            }
          }
          .unsafeToFuture()

      override def expire(key: String, seconds: Long): Future[Boolean] =
        ref
          .modify(oldMap =>
            oldMap
              .get(key)
              .fold(oldMap)(value => oldMap + (key -> value.copy(_2 = System.currentTimeMillis() + (seconds * 1000)))))
          .map(_ => true)
          .unsafeToFuture()
    }
}
