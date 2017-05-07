package ru.makkarpov.textg

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import info.mukel.telegrambot4s.api.BotBase
import info.mukel.telegrambot4s.methods.{DeleteWebhook, GetUpdates}
import info.mukel.telegrambot4s.models.Update

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

// since implementation in telegrambot4s is a complete shit.
trait OwnPolling { this: BotBase =>
  implicit def actorSystem: ActorSystem
  implicit def actorMaterializer: ActorMaterializer
  implicit def executionContext: ExecutionContext

  val pollingInterval: Int = 30

  private val updates: Source[Update, NotUsed] = {
    type Offset = Long
    type Updates = Seq[Update]
    type OffsetUpdates = Future[(Offset, Updates)]

    val seed: OffsetUpdates = Future.successful((0L, Seq.empty[Update]))

    val iterator = Iterator.iterate(seed) {
      _ flatMap {
        case (offset, updates) =>
          val maxOffset = updates.map(_.updateId).fold(offset)(_ max _)
          request(GetUpdates(Some(maxOffset + 1), timeout = Some(pollingInterval)))
            .recover {
              case NonFatal(e) =>
                logger.error("GetUpdates failed", e)
                Seq.empty[Update]
            }
            .map { (maxOffset, _) }
      }
    }

    val parallelism = Runtime.getRuntime().availableProcessors()

    val updateGroups =
      Source.fromIterator(() => iterator)
        .mapAsync(parallelism)(
          _ map {
            case (_, updates) => updates
          })

    updateGroups.mapConcat(_.to) // unravel groups
  }

  override def run(): Unit = {
    request(DeleteWebhook).onComplete {
      case Success(true) =>
        updates
          .runForeach(
            update =>
              try
                onUpdate(update)
              catch {
                case NonFatal(e) =>
                  logger.error("Caught exception in update handler", e)
              }
          ) // sync

      case Success(false) =>
        logger.error("Failed to clear webhook")

      case Failure(e) =>
        logger.error("Failed to clear webhook", e)
    }
  }

  override def shutdown(): Future[_] = {
    actorSystem.terminate()
  }
}
