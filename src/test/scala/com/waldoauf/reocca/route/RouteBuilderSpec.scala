package com.waldoauf.reocca.route

/**
 * Created by klm10896 on 2/25/2015.
 */

import com.waldoauf.reocca.{Cache, Reocca}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.mutable.Specification
import spray.http.{IllegalUriException, StatusCodes}
import spray.routing.ExceptionHandler
import spray.testkit.Specs2RouteTest
import spray.util.LoggingContext


class RouteBuilderSpec extends Specification with Specs2RouteTest with Reocca {
  // connects the DSL to the test ActorSystem
  implicit def actorRefFactory = system

  implicit def reoccaExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case e: IllegalUriException =>
        requestUri { uri =>
          println(s"bad url ${uri}")
          complete(StatusCodes.BadRequest, s"error in '${uri}': ${e.getMessage}")}
      case somethingElse : RuntimeException =>
        requestUri { uri =>
          println(s"some exception ${somethingElse}")
          complete(StatusCodes.NotAcceptable, s"error in '${uri}': ${somethingElse}")
        }
    }

  val testCache =
    """[
          { "target" : {
            "name" : "todos/urgent",
            "replay" : true, "forward" : false, "record" : false,
            "minSimDelay" : "300", "maxSimDelay" : "500",
            "keyGeneratorName" : "default",
            "keyGeneratorParameter" : "pathFilter{1}",
            "url" : "http:ERROR//localhost:8883/todos",
            "filterNamespace" : true,
            "skipHttpHeaders" : true,
            "entries" : [
             {    "key" : "inprogress",
                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
                  "response" : {"objective" : "get this working"}
             },
             {    "key" : "replay/forward",
                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
                  "response" : {"objective" : "get this working"}
             },
             {   "key" : "inprogress/late",
                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
                  "response" : {"objective" : "get this working late"}
             }      ]
          }},
          { "target" : {
            "name" : "todos/toforward",
            "replay" : true, "forward" : true, "record" : false,
            "keyGeneratorName" : "default",
            "keyGeneratorParameter" : "pathFilter{1}",
            "url" : "http:ERROR//localhost:8883/todos/inprogress",
            "filterNamespace" : true,
            "skipHttpHeaders" : true,
            "decoratorName" : "default",
            "entries" :   [
                   {   "key" : "",
                        "method" : "put", "requestHeader" : "tbd", "responseHeader" : "tbd",
                        "response" : {"objective" : "put to work"}
                   },
                   {   "key" : "",
                        "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
                        "response" : {"objective" : "get this working too"}
                   }
                          ]
          }},
          { "target" : {
            "name" : "todos/urgent/inprogress",
            "replay" : true, "forward" : false, "record" : false,
            "keyGeneratorName" : "default",
            "keyGeneratorParameter" : "pathFilter{1}",
            "url" : "http://localhost:8883/todos/inprogress",
            "filterNamespace" : true,
            "skipHttpHeaders" : true,
            "decoratorName" : "default",
            "entries" : [
                   {   "key" : "",
                        "method" : "put", "requestHeader" : "tbd", "responseHeader" : "tbd",
                        "response" : {"objective" : "put to work"}
                   },
                   {   "key" : "",
                        "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
                        "response" : {"objective" : "get this working too"}
                   },
                   {   "key" : "",
                        "method" : "post", "requestHeader" : "tbd", "responseHeader" : "tbd",
                        "response" : {"id" : "42"}
                   }
                    ]
          }}
        ]"""


  val testCacheObj = parse(testCache)
  Cache.putCache("test", testCacheObj)
  var testRoute = buildRoute()


  "\nReocca with the  test cache " should {
    """return a NOT FOUND error for GET requests to path 'todos/urgent/ixxxnprogress'""" in {
    Get("/todos/urgent/ixxxnprogress") ~> testRoute ~> check {
      status === StatusCodes.NotFound
    }
  }
    """respond including "get this working" for GET requests to path 'todos/urgent/inprogress'""" in {
      Get("/test/todos/urgent/inprogress") ~> testRoute ~> check {
        status === StatusCodes.OK
        entity.asString.contains("get this working")
      }
    }
    """handle a forwarding requests to a bad url by giving an error status code and message""" in {
      Get("/test/todos/toforward") ~> sealRoute(testRoute) ~> check {
        handled must beFalse
      }
    }
    """respond including "put to work" for PUT requests""" in {
      Put("/test/todos/urgent/inprogress") ~> testRoute ~> check {
        status === StatusCodes.OK
        entity.asString.contains("put to work")
      }
    }
    """respond including "id" for POST requests """ in {
      Post("/test/todos/urgent/inprogress") ~> testRoute ~> check {
        status === StatusCodes.OK
        entity.asString.contains("id")
      }
    }
    //    """handle get requests to other paths in a default way""" in {
    //      Get("/todos/urgentaaa") ~> testRoute ~> check {
    //        status === StatusCodes.NotFound
    //        handled must beTrue
    //      }
    //    }
  }
}
