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
import spray.http.StatusCodes
import spray.json._
import spray.routing.HttpService
import spray.testkit.Specs2RouteTest

import JsonConversions._


class RouteBuilderSpec extends Specification with Specs2RouteTest with Reocca {
  // connects the DSL to the test ActorSystem
  implicit def actorRefFactory = system
  val testCache =
    """[
           {   "name" : "todos/urgent",
                "key" : "inprogress/late",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/todos",
                "method" : "get", "header" : "tbd",
                "response" : {"objective" : "get this working"}
           },
           {   "name" : "todos/urgent",
                "key" : "inprogress/late",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/todos",
                "method" : "get", "header" : "tbd",
                "response" : {"objective" : "get this working"}
           },
           {   "name" : "todos/urgent/inprogress",
                "key" : "",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/todos",
                "method" : "put", "header" : "tbd",
                "response" : {"objective" : "put to work"}
           },
           {   "name" : "tadas",
                "key" : "",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/tadas",
           "method" : "get", "header" : "tbd",
                "response" : {"objective" : "get this working too"}
           },
           {   "name" : "todos",
                "key" : "",
                "replay" : true, "forward" : false,"record" : false,
                "url" : "http:localhost:8883/tadas",
                "method" : "post", "header" : "tbd",
                "response" : {"id" : "42"}
            }]"""


  val testCacheObj = parse(testCache)
  cacheMap.put("test", testCacheObj)
  var testRoute = buildRoute(cacheMap, null)


  "\nReocca, with the temporary test cache, " should { """return a NOT FOUND error for GET requests to path 'todos/urgent/inprogress'""" in {
    Get("/test/todos/urgent/inprogress") ~> testRoute ~> check {
      status === StatusCodes.NotFound
    }
  }
  }
  "\nReocca, with the temporary test cache, " should { """return a response including "get this working" for GET requests to path 'todos/urgent/inprogress/late'""" in {
    Get("/test/todos/urgent/inprogress/late") ~> testRoute ~> check {
      status === StatusCodes.OK
      entity.asString.contains("""{"objective":"get this working"}""")    }
  }
  }

  "Reocca, with the temporary test cache, " should { """return a response including "get this working too" for GET requests to path '/test/tadas'""" in {
    Get("/test/tadas") ~> testRoute ~> check {
      status === StatusCodes.OK
      entity.asString.contains("get this working too")
    }
  }
  }

  "Reocca, with the temporary test cache, " should { """return a response including "put to work" for PUT requests to path '/test/todos/urgent/inprogress'""" in {
    Put("/test/todos/urgent/inprogress") ~> testRoute ~> check {
      status === StatusCodes.OK
      entity.asString.contains("put to work")
    }
  }
  }
  "Reocca, with the temporary test cache, " should { """return a response including "id" for POST requests to path 'test/todos'""" in {
    Post("/test/todos") ~> testRoute ~> check {
      status === StatusCodes.OK
      entity.asString.contains("id")
    }
  }
  }
  "Reocca, with the temporary test cache, " should { """handle get requests to other paths in a default way""" in {
    Get("/todos/urgentaaa") ~> testRoute ~> check {
      status === StatusCodes.NotFound
      handled must beTrue
    }
  }
  }
}
