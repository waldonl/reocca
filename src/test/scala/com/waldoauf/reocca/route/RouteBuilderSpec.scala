package com.waldoauf.reocca.route

/**
 * Created by klm10896 on 2/25/2015.
 */

import com.waldoauf.reocca.{Cache, Reocca}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.mutable.Specification
import shapeless.HNil
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
  val routep =
    parameterMap { params =>
      def paramString(param: (String, String)): String = s"""${param._1} = '${param._2}'"""
      complete(s"The parameters are ${params.map(paramString).mkString(", ")}")
    }

  Get("/?color=blue&count=42") ~> routep ~> check {
    responseAs[String] === "The parameters are color = 'blue', count = '42'"
  }
  Get("/?x=1&x=2") ~> routep ~> check {
    responseAs[String] === "The parameters are x = '2'"
  }
  val col = Symbol("color")
  val ppp = col ! "blue"
  val sss = 'size ! "big"
  val pppp = sss :: ppp :: HNil

  val routeDifferentReqParam = parameter(pppp) {complete("bigblue")} ~ complete("parms nok")
  Get("?color=blue&size=big") ~> routeDifferentReqParam ~> check {
    responseAs[String] === "bigblue"
  }
  Get("?color=red&size=big") ~> routeDifferentReqParam ~> check {
    responseAs[String] === "parms nok"
  }
  val routeSeq =
    parameterMap { params => params.get("aap") match {
      case Some("aapje") => complete("parms aap")
      case otherwise => reject()
    }
    } ~
    parameterMap { params => params.get("noot") match {
      case Some("nootje") => complete("parms noot")
      case otherwise => reject()
    }
    }

  Get("/?aap=aapje") ~> routeSeq ~> check {
    responseAs[String] === "parms aap"
  }
  Get("/?noot=nootje") ~> routeSeq ~> check {
    responseAs[String] === "parms noot"
  }
}
