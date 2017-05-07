package ru.makkarpov.textg

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{AnswerInlineQuery, ParseMode, SendMessage, SendPhoto}
import info.mukel.telegrambot4s.models._
import org.apache.commons.codec.binary.Hex
import play.api.Configuration
import play.api.mvc.{Headers, RequestHeader}
import ru.makkarpov.textg.TeXRenderer.TeXError

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by makkarpov on 07.05.17.
  */
class TelegramBot @Inject()(cfg: Configuration, system: ActorSystem, renderer: TeXRenderer) {
  val token = cfg.getString("textg.token").getOrElse(sys.error("Telegram token is not specified"))
  val host = cfg.getString("textg.host").getOrElse(sys.error("No host specified"))
  val botName = cfg.getString("textg.botname").getOrElse(sys.error("No bot name specified"))

  implicit val fakeRequestHeader = new RequestHeader {
    override def clientCertificateChain: Option[Seq[X509Certificate]] = None
    override def secure: Boolean = false
    override def path: String = "/"
    override def id: Long = 0
    override def remoteAddress: String = ""
    override def headers: Headers = Headers()
    override def method: String = "POST"
    override def queryString: Map[String, Seq[String]] = Map.empty
    override def uri: String = s"http://${TelegramBot.this.host}/"
    override def version: String = ""
    override def tags: Map[String, String] = Map.empty
  }

  val lengthLimit = 1024

  val bot = new BotBase with OwnPolling {
    override val token: String = TelegramBot.this.token
    override val logger: Logger = Logger("ru.makkarpov.bot")
    override val client: RequestHandler = new TelegramClientAkka(token)

    override implicit def actorSystem: ActorSystem = TelegramBot.this.system
    override implicit def actorMaterializer: ActorMaterializer = ActorMaterializer()
    override implicit def executionContext: ExecutionContext = TelegramBot.this.system.dispatcher

    private def sha256(s: String): String =
      Hex.encodeHexString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))

    private def escape(s: String): String =
      s.replaceAll("&", "&amp;").replaceAll("\"", "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")

    def formulaReplyMarkup(formula: String): Option[InlineKeyboardMarkup] = Some(InlineKeyboardMarkup(
      inlineKeyboard = Seq(
        Seq(
          InlineKeyboardButton(
            text = "Copy",
            switchInlineQueryCurrentChat = Some(formula)
          )
        )
      )
    ))

    def resultFor(formula: String, settings: RenderSettings): InlineQueryResultPhoto = {
      val link = routes.RenderController.jpg(formula, settings.scale).absoluteURL()

      InlineQueryResultPhoto(
        id = sha256(link),
        photoUrl = link,
        thumbUrl = link,
        replyMarkup = formulaReplyMarkup(formula)
      )
    }

    def resultForError(err: TeXError): InlineQueryResultPhoto = {
      val link = _root_.controllers.routes.Assets.versioned("error.jpg").absoluteURL()

      InlineQueryResultPhoto(
        id = sha256("ERR:" + err.error + ";" + err.formula),
        photoUrl    = link,
        thumbUrl    = link,
        inputMessageContent = Some(InputTextMessageContent(
          messageText = "<strong>Parse error: </strong> " + escape(err.error) + "\n\n<code>" + escape(err.formula) + "</code>",
          parseMode = Some("html")
        ))
      )
    }

    def lengthLimitError(formula: String): TeXError =
      TeXError(formula, s"Formula is too long (${formula.length} characters), limit is $lengthLimit.")

    def reply(text: String)(implicit msg: Message): Unit =
      request(SendMessage(chatId = Left(msg.chat.id), text = text, parseMode = Some(ParseMode.HTML)))

    def replyError(err: TeXError)(implicit msg: Message): Unit =
      reply("<strong>Parse error: </strong> " + escape(err.error) + "\n\n<code>" + escape(err.formula) + "</code>")

    def replyPhoto(formula: String, settings: RenderSettings)(implicit msg: Message): Unit = {
      val link = routes.RenderController.jpg(formula, settings.scale).absoluteURL()

      request(SendPhoto(
        chatId = Left(msg.chat.id),
        photo = Right(link),
        replyMarkup = formulaReplyMarkup(formula)
      ))
    }

    override def onInlineQuery(inlineQuery: InlineQuery): Unit = {
      val formula = inlineQuery.query

      if (formula.length > lengthLimit) {
        request(AnswerInlineQuery(
          inlineQueryId = inlineQuery.id,
          results = resultForError(lengthLimitError(formula)) :: Nil,
          isPersonal = Some(false)
        ))

        return
      }

      val settings = RenderSettings(60, transparent = false)

      val responses =
        renderer.testRender(formula, settings) map {
          case Some(err) => resultForError(err)
          case None => resultFor(formula, settings)
        } map { _ :: Nil }

      for (r <- responses)
        request(AnswerInlineQuery(
          inlineQueryId = inlineQuery.id,
          results = r,
          isPersonal = Some(false)
        ))
    }

    override def onMessage(message: Message): Unit = {
      implicit val implicitMessage = message

      val commandPrefix = "/tex"
      val botPrefix = commandPrefix + "@" + botName

      message.text match {
        case None =>
        case Some("/start") => reply("Hello! Send me something and I will render it as a LaTeX AMS expression.")
        case Some(query) =>
          val formula =
            if (query.startsWith(commandPrefix + " ")) query.substring(commandPrefix.length + 1)
            else if (query.startsWith(botPrefix + " ")) query.substring(botPrefix.length + 1)
            else query

          if (formula.length > lengthLimit) {
            replyError(lengthLimitError(formula))
            return
          }

          val settings = RenderSettings(60, transparent = false)

          renderer.testRender(formula, settings) foreach {
            case Some(err) => replyError(err)
            case None => replyPhoto(formula, settings)
          }
      }
    }
  }

  bot.run()
}
