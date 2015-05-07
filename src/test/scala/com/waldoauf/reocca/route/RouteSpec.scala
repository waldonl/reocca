package com.waldoauf.reocca.route

/**
 * Created by klm10896 on 2/25/2015.
 */

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest


class RouteSpec extends Specification with Specs2RouteTest /*with Reocca*/ {
//  // connects the DSL to the test ActorSystem
//  implicit def actorRefFactory = system
//
//  implicit def reoccaExceptionHandler(implicit log: LoggingContext) =
//    ExceptionHandler {
//      case e: IllegalUriException =>
//        requestUri { uri =>
//          println(s"bad url ${uri}")
//          complete(StatusCodes.BadRequest, s"error in '${uri}': ${e.getMessage}")}
//      case somethingElse : RuntimeException =>
//        requestUri { uri =>
//          println(s"some exception ${somethingElse}")
//          complete(StatusCodes.NotAcceptable, s"error in '${uri}': ${somethingElse}")
//        }
//    }

//  val testCache =
//    """[
//          { "target" : {
//            "name" : "todos/urgent",
//            "replay" : true, "forward" : false, "record" : false,
//            "minSimDelay" : "300", "maxSimDelay" : "500",
//            "keyGeneratorName" : "default",
//            "keyGeneratorParameter" : "pathFilter{1}",
//            "url" : "http:ERROR//localhost:8883/todos",
//            "filterNamespace" : true,
//            "skipHttpHeaders" : true,
//            "entries" : [
//             {    "key" : "inprogress",
//                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                  "response" : {"objective" : "get this working"}
//             },
//             {    "key" : "replay/forward",
//                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                  "response" : {"objective" : "get this working"}
//             },
//             {   "key" : "inprogress/late",
//                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                  "response" : {"objective" : "get this working late"}
//             },
//             {   "key" : "inprogress/late?intent=test",
//                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                  "response" : {"objective" : "get this test working late"}
//             },
//             {   "key" : "inprogress/late?intent=test&scope=more",
//                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                  "response" : {"objective" : "get this other test working late"}
//             }      ]
//          }},
//          { "target" : {
//            "name" : "todos/toforward",
//            "replay" : true, "forward" : true, "record" : false,
//            "keyGeneratorName" : "default",
//            "keyGeneratorParameter" : "pathFilter{1}",
//            "url" : "http:ERROR//localhost:8883/todos/inprogress",
//            "filterNamespace" : true,
//            "skipHttpHeaders" : true,
//            "decoratorName" : "default",
//            "entries" :   [
//                   {   "key" : "",
//                        "method" : "put", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                        "response" : {"objective" : "put to work"}
//                   },
//                   {   "key" : "",
//                        "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                        "response" : {"objective" : "get this working too"}
//                   }
//                          ]
//          }},
//          { "target" : {
//            "name" : "todos/urgent/inprogress",
//            "replay" : true, "forward" : false, "record" : false,
//            "keyGeneratorName" : "default",
//            "keyGeneratorParameter" : "pathFilter{1}",
//            "url" : "http://localhost:8883/todos/inprogress",
//            "filterNamespace" : true,
//            "skipHttpHeaders" : true,
//            "decoratorName" : "default",
//            "entries" : [
//                   {   "key" : "",
//                        "method" : "put", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                        "response" : {"objective" : "put to work"}
//                   },
//                   {   "key" : "",
//                        "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                        "response" : {"objective" : "get this working too"}
//                   },
//                   {   "key" : "",
//                        "method" : "post", "requestHeader" : "tbd", "responseHeader" : "tbd",
//                        "response" : {"id" : "42"}
//                   }
//                    ]
//          }}
//        ]"""
//
//
//  val testCacheObj = parse(testCache)
//  Cache.putCache("test", testCacheObj)
//  var testRoute = buildRoute()


//  "\nReocca with the  test cache " should {
//
//    """respond including "get this working" for GET requests to path 'todos/urgent/inprogress'""" in {
//      Get("/test/todos/urgent/inprogress") ~> testRoute ~> check {
//        status === StatusCodes.OK
//        entity.asString.contains("get this working")
//      }
//    }
//    """find a matching response based both on target entry name and on target entries key""" in {
//      Get("/test/todos/urgent/inprogress/late") ~> testRoute ~> check {
//        status === StatusCodes.OK
//        entity.asString.contains("get this working late")
//      }
//    }
//    """find a matching response based both on target entry name, entries key including request params""" in {
//      Get("/test/todos/urgent/inprogress/late?intent=test") ~> testRoute ~> check {
//        status === StatusCodes.OK
//        entity.asString.contains("get this test working late")
//      }
//    }
//    """find a matching response based both on target entry name, entries key including multiple request params in different order""" in {
//      Get("/test/todos/urgent/inprogress/late?scope=more&intent=test") ~> testRoute ~> check {
//        status === StatusCodes.OK
//        entity.asString.contains("get this other test working late")
//      }
//    }
//    """handle a forwarding requests to a bad url by giving an error status code and message""" in {
//      Get("/test/todos/toforward") ~> sealRoute(testRoute) ~> check {
//        handled must beFalse
//      }
//    }
//    """respond including "put to work" for PUT requests""" in {
//      Put("/test/todos/urgent/inprogress") ~> testRoute ~> check {
//        status === StatusCodes.OK
//        entity.asString.contains("put to work")
//      }
//    }
//    """respond including "id" for POST requests """ in {
//      Post("/test/todos/urgent/inprogress") ~> testRoute ~> check {
//        status === StatusCodes.OK
//        entity.asString.contains("id")
//      }
//    }
    //    """PUT  alters the value of a designated field""" in {
    //      Put("/REOCCA/test/todos/urgent", FormData(Seq("url" -> "loempia"))) ~> testRoute ~> check {
    //        status === StatusCodes.OK
    //      }
    //    }
    //    """handle get requests to other paths in a default way""" in {
    //      Get("/todos/urgentaaa") ~> testRoute ~> check {
    //        status === StatusCodes.NotFound
    //        handled must beTrue
    //      }
    //    }
//  }
  // *********************************************
//  val routeFF =
//    formFields("color", 'age.as[Int]) { (color, age) =>
//      complete(s"The color is '$color' and the age ten years ago was ${age - 10}")
//    }
//
//  Post("/", FormData(Seq("color" -> "blue", "age" -> "68"))) ~> routeFF ~> check {
//    responseAs[String] === "The color is 'blue' and the age ten years ago was 58"
//  }
//  val routep =
//    parameterMap { params =>
//      def paramString(param: (String, String)): String = s"""${param._1} = '${param._2}'"""
//      complete(s"The parameters are ${params.map(paramString).mkString(", ")}")
//    }

//  Get("/?color=blue&count=42") ~> routep ~> check {
//    responseAs[String] === "The parameters are color = 'blue', count = '42'"
//  }
//  Get("/?x=1&x=2") ~> routep ~> check {
//    responseAs[String] === "The parameters are x = '2'"
//  }
//  val col = Symbol("color")
//  val ppp = col ! "blue"
//  val sss = 'size ! "big"
//  val pppp = sss :: ppp :: HNil
//
//  val routeDifferentReqParam = parameter(pppp) {complete("bigblue")} ~ complete("parms nok")
//  Get("?color=blue&size=big") ~> routeDifferentReqParam ~> check {
//    responseAs[String] === "bigblue"
//  }
//  Get("?color=red&size=big") ~> routeDifferentReqParam ~> check {
//    responseAs[String] === "parms nok"
//  }
//  val routeSeq =
//    parameterMap { params => params.get("aap") match {
//      case Some("aapje") => complete("parms aap")
//      case otherwise => reject()
//    }
//    } ~
//      parameterMap { params => params.get("noot") match {
//        case Some("nootje") => complete("parms noot")
//        case otherwise => reject()
//      }
//      }
//
//  Get("/?aap=aapje") ~> routeSeq ~> check {
//    responseAs[String] === "parms aap"
//  }
//  Get("/?noot=nootje") ~> routeSeq ~> check {
//    responseAs[String] === "parms noot"
//  }
//  //  case class CacheTarget(var name: String = "",
//  //                         var replay: Boolean = true,
//  //                         var forward: Boolean = false,
//  //                         var record: Boolean = false,
//  //                         var minSimDelay: BigInt = 0,
//  //                         var maxSimDelay: BigInt = 0,
//  //                         var keyGeneratorName: String = "",
//  //                         var keyGeneratorParameter: String = "",
//  //                         var url: String = "",
//  //                         var filterNamespace: Boolean = false,
//  //                         var skipHttpHeaders: Boolean = false,
//  //                         var decoratorName: String = "")
//  case class Person(name: String, /*favoriteNumber: Int,*/replay:Boolean = true, url: String)
//  object PersonJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
//    implicit val PortofolioFormats = jsonFormat3(Person)
//  }
//
//  val routeee = post {
//    entity(as[FormData]) { person => complete("here are the fields")
//    }
//  }


  //  "The UrlEncodedFormDataUnmarshaller" should {
  //    "correctly unmarshal HTML form content with one element" in {
  //      HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`), "secret=h%C3%A4ll%C3%B6").as[FormData] ==
  //        Right(FormData(Map("secret" -> "hällö")))}
  //    "correctly unmarshal HTML form content with one element with default encoding utf-8" in {
  //      HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), "secret=h%C3%A4ll%C3%B6").as[FormData] ==
  //        Right(FormData(Map("secret" -> "hällö")))}
  //    "correctly unmarshal HTML form content with three fields" in {
  //      HttpEntity(MediaTypes.`application/x-www-form-urlencoded`, "email=test%40there.com&password=&username=dirk").as[FormData] ==
  //        Right(FormData(Map("email" -> "test@there.com", "password" -> "", "username" -> "dirk")))
  //    }
  //    "correctly unmarshal duplicate fields" in {
  //      HttpEntity(MediaTypes.`application/x-www-form-urlencoded`, "a=42&b=23&a=32").as[FormData] ===
  //        Right(FormData(Seq("a" -> "42", "b" -> "23", "a" -> "32")))
  //    }
  //
  //    "be lenient on empty key/value pairs" in {
  //      HttpEntity(MediaTypes.`application/x-www-form-urlencoded`, "&key=value&&key2=&").as[FormData] ===
  //        Right(FormData(Seq("" -> "", "key" -> "value", "" -> "", "key2" -> "", "" -> "")))
  //    }
  //    "reject illegal form content" in {
  //      val Left(MalformedContent(msg, _)) = HttpEntity(MediaTypes.`application/x-www-form-urlencoded`, "key=really=not_good").as[FormData]
  //      msg === "Illegal form content, unexpected character '=' at position 10: \nkey=really=not_good\n          ^\n"
  //    }
  //  }
}
