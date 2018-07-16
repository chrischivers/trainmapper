import cats.effect.IO
import fs2.Stream
import org.scalatest.Assertion

package object trainmapper {

  def evaluateStream[T](f: Stream[IO, Assertion]) = f.compile.drain.unsafeRunSync()
}
