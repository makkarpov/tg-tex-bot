package ru.makkarpov.textg
import play.api.{Configuration, Environment}
import play.api.inject.Binding

import scala.concurrent.ExecutionContext

/**
  * Created by makkarpov on 07.05.17.
  */
class Module extends play.api.inject.Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ExecutionContext].qualifiedWith("render").to(bind[ExecutionContext]),
    bind[TelegramBot].toSelf.eagerly()
  )
}
