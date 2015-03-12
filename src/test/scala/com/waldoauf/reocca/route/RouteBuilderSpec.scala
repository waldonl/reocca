package com.waldoauf.reocca.route

/**
 * Created by klm10896 on 2/25/2015.
 */

import com.waldoauf.reocca.{JsonConversions, ApiService}
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.mutable.Specification
import spray.json._
import spray.routing.HttpService
import spray.testkit.Specs2RouteTest

import JsonConversions._


class RouteBuilderSpec extends Specification with Specs2RouteTest with ApiService with HttpService {
  // connects the DSL to the test ActorSystem
  implicit def actorRefFactory = system


    "\nReocca, with the temporary init cache, " should {"""return a response including "get this working" for GET requests to path 'todos'""" in {
      Get("/init/todos") ~> buildRoute(cacheMap) ~> check {entity.asString.contains("get this working")}}}

    "Reocca, with the temporary init cache, " should {"""return a response including "get this working too" for GET requests to path 'todos'""" in {
      Get("/init/tadas") ~> buildRoute(cacheMap) ~> check {entity.asString.contains("get this working too")}}}

    "Reocca, with the temporary init cache, " should {"""return a response including "put to work" for PUT requests to path 'todos'""" in {
      Put("/init/todos") ~> buildRoute(cacheMap) ~> check {entity.asString.contains("put to work")}}}
    "Reocca, with the temporary init cache, " should {"""return a response including "id" for POST requests to path 'todos'""" in {
      Post("/init/todos") ~> buildRoute(cacheMap) ~> check {entity.asString.contains("id")}}}
    "Reocca, with the temporary init cache, " should {"""leave get requests to other paths unhandled""" in {
      Get("/unknown") ~> buildRoute(cacheMap) ~> check {handled must beFalse}}}
}
