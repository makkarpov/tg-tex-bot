package ru.makkarpov.textg

import java.awt.{Color, Insets}
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.inject.{Inject, Named, Singleton}

import org.scilab.forge.jlatexmath.{ParseException, TeXConstants, TeXFormula, TeXIcon}
import ru.makkarpov.textg.TeXRenderer.TeXError

import scala.concurrent.{ExecutionContext, Future}

object TeXRenderer {
  case class TeXError(formula: String, error: String)
}

@Singleton
class TeXRenderer @Inject()(implicit @Named("render") ec: ExecutionContext) {
  val errorImage = ImageIO.read(getClass.getResourceAsStream("/error.jpg"))

  private def createIcon(formula: String, settings: RenderSettings): TeXIcon = {
    val f = new TeXFormula(formula)
    val icon = new f.TeXIconBuilder().setSize(settings.scale).setStyle(TeXConstants.STYLE_DISPLAY).build()
    icon.setInsets(new Insets(5, 5, 5, 5))
    icon
  }

  def testRender(formula: String, settings: RenderSettings): Future[Option[TeXError]] = Future {
    try {
      createIcon(formula, settings)
      None
    } catch {
      case e: ParseException =>
        Some(TeXError(formula, e.getMessage))
    }
  }

  def render(formula: String, settings: RenderSettings): Future[BufferedImage] = Future {
    try {
      val icon = createIcon(formula, settings)
      val imgType = if (settings.transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
      val img = new BufferedImage(icon.getIconWidth, icon.getIconHeight, imgType)
      val g = img.createGraphics()

      if (!settings.transparent) {
        g.setColor(Color.white)
        g.fillRect(0, 0, img.getWidth, img.getHeight)
      }

      icon.paintIcon(null, g, 0, 0)
      g.dispose()

      img
    } catch {
      case e: ParseException =>
        errorImage
    }
  }
}
