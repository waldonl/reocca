package com.waldoauf.reocca

import akka.actor.{ ActorSystem, Props }
import akka.io.IO
import org.json4s.jackson.JsonMethods
import spray.json._
import spray.can.Http
import org.json4s.JsonMethods
import org.json4s.JField
import org.json4s.JNull
import org.json4s.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JValue

import scala.io.Source

object Boot extends App {
  implicit val system = ActorSystem()


  // fetch configuration from resources/application.config
  val interface = system.settings.config getString "service.interface"
  val port      = system.settings.config getInt    "service.port"
  // create and start our service actor
  val service = system.actorOf(Props(classOf[ApiServiceActor], interface, port), "api")
  service ! ("init", JNull)

}
