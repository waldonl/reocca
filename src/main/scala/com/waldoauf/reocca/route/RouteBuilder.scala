package com.waldoauf.reocca.route

import org.json4s.JsonAST.{JValue, JObject}
import shapeless.get
import spray.routing.Route
import akka.actor.{ActorRefFactory, Actor, ActorRef}

import spray.util._
import spray.http._
import spray.http.MediaTypes._
import spray.http.HttpHeaders.{`Content-Type`, Location}
import spray.routing.HttpService

/**
 * Created by klm10896 on 2/25/2015.
 */
trait RouteBuilder extends HttpService {
def build(json : JValue) : Route = get{_.complete("practice")}

}
