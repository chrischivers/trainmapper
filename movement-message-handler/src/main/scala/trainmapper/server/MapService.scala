package trainmapper.server

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Effect
import cats.implicits._
import org.http4s.CacheDirective.`no-cache`
import org.http4s.MediaType.`text/html`
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Cache-Control`, `Content-Type`}
import org.http4s.{Charset, StaticFile}
import scalatags.Text.TypedTag
import scalatags.Text.all.{Modifier, script, src}

object MapService {

  val jsScript = "trainmapper-frontend-fastopt.js"
  val jsDeps   = "trainmapper-frontend-jsdeps.js"

  val jsScripts: Seq[Modifier] = {
    import scalatags.Text.all._
    List(
      script(src := jsScript),
      script(src := jsDeps)
    )
  }

  val index: Seq[Modifier] = {
    import scalatags.Text.all._
    Seq(
      h1(
        style := "align: center;",
        "Train Mapper"
      ),
      div(height := "600px", id := "map-canvas")
    )
  }

  def template(headContent: Seq[Modifier],
               bodyContent: Seq[Modifier],
               scripts: Seq[Modifier],
               cssComps: Seq[Modifier]): TypedTag[String] = {
    import scalatags.Text.all._

    html(
      head(
        headContent,
        cssComps
      ),
      body(
        bodyContent,
        scripts
      )
    )

  }

  def apply[F[_]](googleMapsApiKey: String)(implicit effect: Effect[F]) = {

    /*
    Based one example in:
    https://github.com/davenport-scala/http4s-scalajsexample
     */

    import scalatags.Text.all._

    object dsl extends Http4sDsl[F]
    import dsl._

    val googleMapsDep =
      s"https://maps.googleapis.com/maps/api/js?key=$googleMapsApiKey&callback=initialize"

    def getResource(pathInfo: String) = effect.delay(this.getClass.getResource(pathInfo))

    val supportedStaticExtensions =
      List(".html", ".js", ".map", ".css", ".png", ".ico")

    org.http4s.HttpService[F] {
      case GET -> Root / "map" =>
        Ok(template(Seq(), index, jsScripts :+ script(src := googleMapsDep), Seq()).render)
          .map(
            _.withContentType(`Content-Type`(`text/html`, Charset.`UTF-8`))
              .putHeaders(`Cache-Control`(NonEmptyList.of(`no-cache`())))
          )

      case req if supportedStaticExtensions.exists(req.pathInfo.endsWith) =>
        StaticFile
          .fromResource[F](req.pathInfo, req.some)
          .orElse(OptionT.liftF(getResource(req.pathInfo)).flatMap(StaticFile.fromURL[F](_, req.some)))
          .map(_.putHeaders(`Cache-Control`(NonEmptyList.of(`no-cache`()))))
          .fold(NotFound())(_.pure[F])
          .flatten
    }
  }
}
