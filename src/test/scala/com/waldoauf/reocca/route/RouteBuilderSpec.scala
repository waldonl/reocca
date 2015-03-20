package com.waldoauf.reocca.route

/**
 * Created by klm10896 on 2/25/2015.
 */

import akka.actor.Props
import com.waldoauf.reocca.{JsonConversions, Reocca}
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.mutable.Specification
import spray.json._
import spray.routing.HttpService
import spray.testkit.Specs2RouteTest

import JsonConversions._


class RouteBuilderSpec extends Specification with Specs2RouteTest with Reocca {
  // connects the DSL to the test ActorSystem
  implicit def actorRefFactory = system
  val testCache =
    """[
           {   "name" : "todos",
                "key" : "*",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/todos",
                "method" : "get", "header" : "tbd",
                "response" : {"objective" : "get this working"}
           },
           {   "name" : "todos",
                "key" : "*",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/todos",
                "method" : "put", "header" : "tbd",
                "response" : {"objective" : "put to work"}
           },
           {   "name" : "tadas",
                "key" : "*",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/tadas",
           "method" : "get", "header" : "tbd",
                "response" : {"objective" : "get this working too"}
           },
           {   "name" : "todos",
                "key" : "*",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/tadas",
                "method" : "post", "header" : "tbd",
                "response" : {"id" : "42"}
            }]"""


  val testCacheObj = parse(testCache)
  cacheMap.put("test", testCacheObj)
  var testRoute = buildRoute(cacheMap, null)


    "\nReocca, with the temporary test cache, " should {"""return a response including "get this working" for GET requests to path 'todos'""" in {
      Get("/test/todos") ~> testRoute ~> check {entity.asString.contains("get this working")}}}

    "Reocca, with the temporary test cache, " should {"""return a response including "get this working too" for GET requests to path 'todos'""" in {
      Get("/test/tadas") ~> testRoute ~> check {entity.asString.contains("get this working too")}}}

    "Reocca, with the temporary test cache, " should {"""return a response including "put to work" for PUT requests to path 'todos'""" in {
      Put("/test/todos") ~> testRoute ~> check {entity.asString.contains("put to work")}}}
    "Reocca, with the temporary test cache, " should {"""return a response including "id" for POST requests to path 'todos'""" in {
      Post("/test/todos") ~> testRoute ~> check {entity.asString.contains("id")}}}
    "Reocca, with the temporary test cache, " should {"""handle get requests to other paths in a default way""" in {
      Get("/unknown") ~> testRoute ~> check {handled must beTrue}}}
}
