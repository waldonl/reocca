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

  // create and start our service actor
  val service = system.actorOf(Props[ApiServiceActor], "api")

  // fetch configuration from resources/application.config
  val interface = system.settings.config getString "service.interface"
  val port      = system.settings.config getInt    "service.port"
//  val cacheconf = system.settings.config getString "service.file"
//  println("cacheconf: " + cacheconf)
//
//  val cacheconfJson : JValue = {
//    val cacheStr =
//    if (!cacheconf.contentEquals("nofile")) {
//      Source.fromFile(cacheconf).mkString
//    } else "[]"
//    JsonMethods.parse(cacheStr)
//  }
//  println("found file: " + cacheconfJson)

  // start a new HTTP server with our service actor as the handler
  IO(Http) ! Http.Bind(service, interface, port)
}
