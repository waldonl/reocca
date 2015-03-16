package com.waldoauf.reocca

// the service, actors and paths

import akka.actor._
import akka.io.IO
import spray.can.Http
import spray.http.{HttpRequest, StatusCodes, HttpMethods}

import spray.util._
import spray.http.HttpHeaders.{`Content-Type`, Location}
import spray.routing._
import spray.httpx.Json4sJacksonSupport
import org.json4s.jackson.JsonMethods._// .native.JsonMethods._

import org.json4s._
import org.json4s.JsonAST.{JObject, JString, JValue, JField}

/* Used to mix in Spray's Marshalling Support with json4s */
object JsonConversions extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats: Formats = DefaultFormats
}

import scala.collection.mutable.HashMap

class ReoccaPortalActor(interface : String, port : Int) extends Actor with ReoccaPortal {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context
  implicit val system = ActorSystem()
  var routeCount = 0
  var bound = false
  def newRouteRunner(route: Route) = {
    routeCount = routeCount + 1
    context.actorOf(Props(classOf[RouteRunner], route), s"routeRunner${routeCount}")
  }

  var routeReceived : Route = (_ => Unit)
  var httpListener : ActorRef = null
  def receive = {
    case (cacheName: String, jCache : JValue) => {
      println(s"creating new route for ${cacheName}")
      if (cacheName == "init")
        routeReceived = buildRoute(cacheMap, context.self)
      else
        routeReceived = extendRouteWithCache(cacheName, jCache, context.self)
      // start a new HTTP server with our reocca portal actor as the handler
      if (bound) {
        println("unbind")
        httpListener ! Http.Unbind
      } else {
          println("about to bind")
          IO(Http) ! Http.Bind(newRouteRunner(routeReceived), interface, port)
      }
    }
    case Http.Unbound => {
      bound = false
      println("unbound")
      IO(Http) ! Http.Bind(newRouteRunner(routeReceived), interface, port)
    }
    case Http.Bound => {
      bound = true
      println("bound")
      httpListener = context.sender()
    }
  }
}

class RouteRunner(routeToRun : Route) extends Actor with HttpService {
  def actorRefFactory: ActorRefFactory = context

  def receive = runRoute(routeToRun)
}

// Routing embedded in the actor
trait ReoccaPortal extends HttpService {

  // default logging
  implicit val log = LoggingContext.fromActorRefFactory

  val cacheMap = HashMap.empty[String, JValue]
  val initCache =
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

  val initCacheObj = parse(initCache)
  cacheMap.put("init", initCacheObj)

  import JsonConversions._

  var route = buildRoute(cacheMap, null)

  def extendRouteWithCache(cacheName: String, jCache : JValue, routeManager: ActorRef): Route = {
    cacheMap.put(cacheName, jCache)
    buildRoute(cacheMap, routeManager)
  }

  def buildRoute(cacheMap : HashMap[String, JValue], routeManager : ActorRef) : Route = {
    import JsonConversions._
    var result : Option[Route] = None
    for {
      cacheName <- cacheMap.keys
    } {
        cacheMap.get(cacheName) match {
          case None => None
          case Some (cache) => {
            val nextRoute = routePerMethodBuilder(cacheName, "get", filterPathsByMethodName("get", cache) ) ~
              routePerMethodBuilder(cacheName, "put",  filterPathsByMethodName("put", cache) ) ~
              routePerMethodBuilder(cacheName, "delete",  filterPathsByMethodName("delete", cache) ) ~
              routePerMethodBuilder(cacheName, "post",  filterPathsByMethodName("post", cache) )
            result match {
              case None => result = Some(nextRoute)
              case _ => result = Some(nextRoute ~ result.get)
            }
          }
        }
    }
    result.getOrElse((_ => Unit): Route) ~
      path("REOCCA" / Rest) {
        pathRest => {
          put {
            entity(as[JValue]) {
              json => complete {
                println(s"receiving cache named ${pathRest}")
                routeManager ! (pathRest, json)

//                cacheMap.put(pathRest, json)
//                println(s"cacheMap ${cacheMap}")
//                route = buildRoute(cacheMap, routeManager)
                println(s"adding  ${pathRest} to: ${route}")
                JNull
              }
            }
          }
        }
      } ~ complete(StatusCodes.NotFound, cacheMap)
  }
  def filterPathsByMethodName(methName: String, pathObjects: JValue): List[List[JField]] = {
    for {
      JObject(child) <- pathObjects
      JField("method", JString(meth)) <- child
      if meth.equals(methName)
    } yield {
      println(s"created path for method ${methName} to ${child}")
      child
    }
  }

  def routePerMethodBuilder(cacheName: String, methName : String, pathList: List[List[JField]]) : Route = {
    def buildPath(cacheEntry: List[JField]) : Route = {
      var name = ""
      for {
        JField("name", JString(jname)) <- cacheEntry
      } name = jname
      var response : JValue = null
      for {
        JField("response", jresponse) <- cacheEntry
      } response = jresponse
      println(s"creating route for path ${cacheName}/${name} and response ${response}")
      pathPrefix(cacheName / name){
        pathEnd {
          complete(response)
        }
      }
    }

    def buildMethod(methName: String) : Directive0 = methName match {
      case "post" => method(HttpMethods.POST)
      case "delete" => method(HttpMethods.DELETE)
      case "put" => method(HttpMethods.PUT)
      case _ => method(HttpMethods.GET)
    }

    def connectPaths(pathList: List[List[JField]], acc : Option[Route]) : Route = {
      if (pathList.isEmpty) acc match {
        case None => complete("nopathinbuildpaths")
        case Some(route) => route
      } else {
        val innerRoute = buildPath(pathList.head)
        val result = acc match {
          case None => new Some(innerRoute)
          case Some(outer: Route) => new Some(outer ~ innerRoute)
        }
        {connectPaths(pathList.tail, result)}
      }
    }

    //
    buildMethod(methName)(connectPaths(pathList, None: Option[Route]))

  }
}

