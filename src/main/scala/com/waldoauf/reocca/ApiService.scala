package com.waldoauf.reocca

// the service, actors and paths

import akka.actor.Actor
import spray.http.HttpMethods

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

class ApiServiceActor extends Actor with ApiService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing,
  // timeout handling or alternative handler registration
  def receive = runRoute(route)
}

// Routing embedded in the actor
trait ApiService extends HttpService {

  // default logging
  implicit val log = LoggingContext.fromActorRefFactory

  val cacheMap = HashMap.empty[String, JValue]
  var counter = 0L

  import JsonConversions._

  def paths(methName: String, pathObjects: JValue): List[List[JField]] = {
    for {
      JObject(child) <- pathObjects
      JField("method", JString(meth)) <- child
      if meth.equals(methName)
    } yield {
      println(s"created path for method ${methName} to ${child}")
      child
    }
  }

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

  def buildRoute(cacheMap : HashMap[String, JValue]) = {
    cacheMap.get("init") match {
      case None => complete (JNothing)
      case Some (cache) =>
        routePerMethodBuilder("init", "get", paths("get", cache) ) ~
        routePerMethodBuilder("init", "put",  paths("put", cache) ) ~
        routePerMethodBuilder("init", "post",  paths("post", cache) )
    }
  }

//  val gets = paths("get", parse(initCache))
//  val posts = paths("post", parse(initCache))
  val route = buildRoute(cacheMap)

  def routePerMethodBuilder(cacheName: String, methName : String, gets: List[List[JField]]) : Route = {
    def mkPath(cacheEntry: List[JField]) : Route = {
      var name = ""
      for {
        JField("name", JString(jname)) <- cacheEntry
      } name = jname
      var response = parse("[]")
      for {
        JField("response", jresponse) <- cacheEntry
      } response = jresponse
      println(s"creating route for path ${cacheName}/${name} and response ${response}")
      pathPrefix("init" / name){
        pathEnd {
          complete(response)
        }
      }
    }

    def buildMethod(methName: String) : Directive0 = methName match {
      case "post" => method(HttpMethods.POST)
      case "put" => method(HttpMethods.PUT)
      case _ => method(HttpMethods.GET)
    }

    def recurGets(gets: List[List[JField]], acc : Option[Route]) : Route = {
      if (gets.isEmpty) {println(s"route complete ${acc.get}")
        acc.getOrElse(complete("invalid cache"))
      } else {
        val innerRoute = mkPath(gets.head)
        val result = acc match {
          case None => new Some(innerRoute)
          case Some(outer: Route) => new Some(outer ~ innerRoute)
        }
        {recurGets(gets.tail, result)}
      }
    }
    buildMethod(methName) (recurGets(gets,  None : Option[Route]))
  }

  def build(json : JValue) : Route = {
    val resp = (json \\ "key").values.toString
    println("resp: " + resp)
    get{_.complete(resp)} }

}
