package ru.makkarpov.textg

import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.mvc.{Action, Controller, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RenderController @Inject()(renderer: TeXRenderer)(implicit ec: ExecutionContext) extends Controller {
  val log = Logger("ru.makkarpov.render")

  case class RenderingFormat(mime: String, name: String, transparent: Boolean)
  val FormatPng = RenderingFormat("image/png", "png", transparent = true)
  val FormatJpg = RenderingFormat("image/jpeg", "jpeg", transparent = false)

  private def render(format: RenderingFormat, formula: String, scale: Float) = Action.async {
    if (formula.length > 1024) Future.successful(BadRequest("Formula is too long"))
    else if (scale <= 0 || scale > 300) Future.successful(BadRequest("Scale is out of range"))
    else renderer.render(formula, RenderSettings(scale, format.transparent)).map { img =>
      log.info(s"Rendered '$formula' at scale $scale with format ${format.name}")
      val stream = new ByteArrayOutputStream()
      ImageIO.write(img, format.name, stream)
      Ok(stream.toByteArray).as(format.mime)
    }
  }

  def png(formula: String, scale: Float) = render(FormatPng, formula, scale)
  def jpg(formula: String, scale: Float) = render(FormatJpg, formula, scale)
}
