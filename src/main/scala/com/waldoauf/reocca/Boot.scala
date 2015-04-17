package com.waldoauf.reocca

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

object Boot extends App {
  implicit val system = ActorSystem()


  // response scheduling for delayed responses
  val responseScheduler = system.actorOf(Props(classOf[DelayedResponseActor]), "tick")
  val cancellable = system.scheduler.schedule(0 milliseconds, 100 milliseconds, responseScheduler, "tick")

  // fetch configuration from resources/application.config
  val interface = system.settings.config getString "service.interface"
  val port      = system.settings.config getInt    "service.port"
  // create and start our service actor
  val service = system.actorOf(Props(classOf[ReoccaActor], interface, port, responseScheduler), "api")
//  service ! ("init", JNull)

  //
  IO(Http) ! Http.Bind(service, interface, port)
}
