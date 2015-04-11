package com.waldoauf.reocca

// the service, actors and paths

import akka.actor._
import com.waldoauf.reocca.Cache.{NamedCache, cacheMap}
import spray.client.pipelining
import spray.client.pipelining._
import spray.http._
import spray.httpx.Json4sJacksonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.routing._
import spray.util._

import scala.concurrent.Future

// .native.JsonMethods._

import org.json4s.JsonAST.JValue
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


//  val cacheMap = HashMap.empty[String, JValue]

  import JsonConversions._

  var route = buildRoute()

  /**
   * wrapping the route enables hotswapping of the route,
   * whilst the wrapper itself retains bound to the internal spray actor, that is bound to the port
   */
  def routeWrapper = (rc : RequestContext) => {
    route(rc)
  }

  def buildRoute() : Route = {
    import JsonConversions._

    val routeAppendix = path("REOCCA" / Rest) {
      pathRest => {
        put {
          entity(as[JValue]) {
            json => complete {
              println(s"receiving cache named ${pathRest}")
              Cache.putCache(pathRest, json)
              println(s"cacheMap ${cacheMap}")
              route = buildRoute()
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
              routePerMethodBuilder(cacheName, "get", Cache.entriesByMethod("get") ) ~
              routePerMethodBuilder(cacheName, "put", Cache.entriesByMethod("put") ) ~
//              routePerMethodBuilder(cacheName, "delete", Cache.entriesByMethod("delete") ) ~
              routePerMethodBuilder(cacheName, "post", Cache.entriesByMethod("post") )
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

  def routePerMethodBuilder(cacheName : String, methName : String, pathList:List[(TargetEntry, CacheTarget)]) : Route = {
    if (pathList.isEmpty) {
      throw new RuntimeException("empty pathlist")
    }
    def segmentAppender(segments : PathMatcher0, segment : String) : PathMatcher0 = {
      if (segment.isEmpty)
        segments
      else
        if (segments == null) {
          var xx : PathMatcher0 =  segment
          println("create new segment " + segment)
          xx
      }
        else {
          print("/" + segment)
          segments / segment
      }
    }
    def buildPath(pathEntry: (TargetEntry, CacheTarget)) : Route = {
      val targetEntry = pathEntry._1
      val cacheTarget = pathEntry._2
      var segments : PathMatcher0 = cacheName
      for {
        segment <- cacheTarget.name.split("/")
      } segments = segmentAppender(segments, segment)
      for {
        segment <- targetEntry.key.split("/")
      } segments = segmentAppender(segments, segment)

      /**
       * post processing the response received back from a forward
       */
      def complementReplacing(eventualResponse: Future[HttpResponse], oldResponse: JValue): ToResponseMarshallable = {
        import scala.concurrent.ExecutionContext.Implicits.global
        eventualResponse onComplete {
          case util.Success(hr: HttpResponse) => {
            val jrsp = hr.entity.data.asString
            if (cacheTarget.record) {
              println(s"we will update the response to ${jrsp}")
              import org.json4s._
              import org.json4s.jackson.JsonMethods._
              updateResponse(Cache.cacheMap, cacheName, segments, parse(jrsp), oldResponse)
              route = buildRoute()
            }
          }
          case util.Success(somethingElse) =>
            println(s"we got something else: ${somethingElse}")
          case util.Failure(error) =>
            println("we got an error")
        }
        eventualResponse
      }
      def updateResponse(cacheMap: HashMap[String, NamedCache], cacheName: String, segmentsToUpdate: PathMatcher0, newResponse: JValue, oldResponse: JValue) = {
        // TODO
        }

      /**
       * post processing the response received back from a forward
       */
      def complementAppending(eventualResponse: Future[HttpResponse], oldResponse: JValue): ToResponseMarshallable = {
        import scala.concurrent.ExecutionContext.Implicits.global
        eventualResponse onComplete {
          case util.Success(hr: HttpResponse) => {
            val jrsp = hr.entity.data.asString
            if (cacheTarget.record) {
              println(s"we will update the response to ${jrsp}")
              import org.json4s._
              import org.json4s.jackson.JsonMethods._
              appendResponse(cacheMap, cacheName, segments, parse(jrsp), oldResponse)
              route = buildRoute()
            }
          }
          case util.Success(somethingElse) =>
            println(s"we got something else: ${somethingElse}")
          case util.Failure(error) =>
            println("we got an error")
        }
        eventualResponse
      }

      println(s" = path to ${cacheTarget.url} yielding ${targetEntry.response}")
      var url : String = null
      pathPrefix(segments) {
        pathEnd {
          if (cacheTarget.forward) {
            import system1.dispatcher
            var pipeline = pipelining.sendReceive
            def eventualHttpResponse = pipeline {
              Get(cacheTarget.url)
            }
            complete(complementReplacing(eventualHttpResponse, targetEntry.response))
          } else complete(targetEntry.response)
        } ~ {
          if (cacheTarget.forward) {
              (req : RequestContext) => req.withUnmatchedPathMapped((unmapped)  => {
              // determine the superfluous part of the segment
              // append that to the cache url
              // forward to that url
              // when complementing that, add the response as a new entry
              var url = cacheTarget.url + unmapped.toString() // todo : exclude requestparamaters
              unmapped
            })
            import system1.dispatcher
            var pipeline = pipelining.sendReceive
            def eventualHttpResponse = pipeline {
              println(s"forwarding to ${url}")
              Get(url)
            }
            complete(complementReplacing(eventualHttpResponse, targetEntry.response))
          } else complete(targetEntry.response)
          }
      }
    }
    def appendResponse(cacheMap: HashMap[String, NamedCache], cacheName: String, segmentsToUpdate: PathMatcher0, newResponse: JValue, oldResponse: JValue) = {
      // todo work on this after refactoring the cache structure
    }
    def buildMethod(methName: String) : Directive0 = methName match {
      case "post" => method(HttpMethods.POST)
      case "delete" => method(HttpMethods.DELETE)
      case "put" => method(HttpMethods.PUT)
      case _ => method(HttpMethods.GET)
    }

    def connectPaths(pathList: List[(TargetEntry, CacheTarget)], acc : Option[Route]) : Route = {
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
