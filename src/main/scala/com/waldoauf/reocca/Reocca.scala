package com.waldoauf.reocca

// the service, actors and paths

import akka.actor._
import spray.client.pipelining
import spray.client.pipelining._
import spray.http.{HttpMethods, HttpResponse, IllegalUriException, StatusCodes}
import spray.httpx.Json4sJacksonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.routing._
import spray.util._

import scala.collection.mutable
import scala.concurrent.Future

// .native.JsonMethods._

import org.json4s.JsonAST.{JField, JObject, JString, JValue}
import org.json4s._

/* Used to mix in Spray's Marshalling Support with json4s */
object JsonConversions extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats: Formats = DefaultFormats
}

import scala.collection.mutable.HashMap

class ReoccaActor(interface : String, port : Int) extends Actor with Reocca {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

    implicit def reoccaExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case e: IllegalUriException =>
        requestUri { uri =>
          println(s"aaaaai ${uri}")
          complete(StatusCodes.BadRequest, s"error in '${uri}': ${e.getMessage}")
        }
    }

  def receive = runRoute(routeWrapper)
}

// Routing embedded in the actor
trait Reocca extends HttpService {
  implicit val system1 = ActorSystem()

  // default logging
  implicit val log = LoggingContext.fromActorRefFactory


  val cacheMap = HashMap.empty[String, JValue]

  import JsonConversions._

  var route = buildRoute(cacheMap)

  /**
   * wrapping the route enables hotswapping of the route,
   * whilst the wrapper itself retains bound to the internal spray actor, that is bound to the port
   */
  def routeWrapper = (rc : RequestContext) => {
    route(rc)
  }

  def buildRoute(cacheMap : HashMap[String, JValue]) : Route = {
    import JsonConversions._

    val routeAppendix = path("REOCCA" / Rest) {
      pathRest => {
        put {
          entity(as[JValue]) {
            json => complete {
              println(s"receiving cache named ${pathRest}")
              cacheMap.put(pathRest, json)
              println(s"cacheMap ${cacheMap}")
              route = buildRoute(cacheMap)
              println(s"adding  ${pathRest} to: ${route}")
              JNull
            }
          }
        }
      }
    } ~ complete(StatusCodes.NotFound, cacheMap)

    var result : Option[Route] = None
    for {
      cacheName <- cacheMap.keys
    } {
        cacheMap.get(cacheName) match {
          case None => None
          case Some (cache) => {
            val nextRoute =
              routePerMethodBuilder(cacheName, "get", filterPathsByMethodName("get", cache) ) ~
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
    result match {
      case None => routeAppendix
      case Some(route) => route ~ routeAppendix
    }
  }
  def filterPathsByMethodName(methName: String, pathObjects: JValue): List[List[JField]] = {
    for {
      JObject(child) <- pathObjects
      JField("method", JString(meth)) <- child
      if meth.equals(methName)
    } yield {
      child
    }
  }



  def routePerMethodBuilder(cacheName: String, methName : String, pathList: List[List[JField]]) : Route = {
    var response : JValue = null
    def buildPath(cacheEntry: List[JField]) : Route = {
      var segments : PathMatcher0 = cacheName
      for {
        JField("name", JString(jname)) <- cacheEntry
        segment <- jname.split("/")
      } segments = segments / segment
      for {
        JField("key", JString(jkey)) <- cacheEntry
        segment <- jkey.split("/")
      } {
        if (!segment.isEmpty)
          segments = segments / segment
      }
      var responseField : JField = null;
      for (
         field : JField <- cacheEntry
        if (field._1 == "response")
      ) {
        response = field._2
        responseField = field
      }
      var forward = false
      for {
        JField("forward", JBool(jforward)) <- cacheEntry
      } forward = jforward
      var record = false
      for {
        JField("forward", JBool(jrecord)) <- cacheEntry
      } record = jrecord
      var url = ""
      for {
        JField("url", JString(jurl)) <- cacheEntry
      } url = jurl

      /**
       * post processing the response received back from a forward
       */
      def complement(eventualResponse: Future[HttpResponse], oldResponse: JValue): ToResponseMarshallable = {
        import scala.concurrent.ExecutionContext.Implicits.global
        eventualResponse onComplete {
          case util.Success(hr: HttpResponse) => {
            val jrsp = hr.entity.data.asString
            if (record) {
              println(s"we will update the response to ${jrsp}")
              import org.json4s._
              import org.json4s.jackson.JsonMethods._
              updateResponse(cacheMap, cacheName, segments, parse(jrsp), oldResponse)
              route = buildRoute(cacheMap)
            }
          }
          case util.Success(somethingElse) =>
            println(s"we got something else: ${somethingElse}")
          case util.Failure(error) =>
            println("we got an error")
        }
        eventualResponse
      }
      path(segments){
        if (forward) {
          import system1.dispatcher
          var pipeline = pipelining.sendReceive
          def eventualHttpResponse = pipeline {
            Get(url)
          }
          complete(complement(eventualHttpResponse, response))
        } else complete(response)
      }
    }
    def updateResponse(cacheMap: mutable.HashMap[String, JValue], cacheName: String, segmentsToUpdate: PathMatcher0, newResponse: JValue, oldResponse: JValue) = {
      val oldCacheOption = cacheMap.get(cacheName)
      if (oldCacheOption != None) {
        val oldCache : JValue = oldCacheOption.get
        var segments : PathMatcher0 = cacheName
        val newCache : JValue = oldCache.map(cacheEntry => {
          println(s"============ cache entry: ${cacheEntry}")
          if (cacheEntry == oldResponse) {
            println("hit!!!")
            newResponse
          } else cacheEntry
          })
        cacheMap.put(cacheName, newCache)
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
