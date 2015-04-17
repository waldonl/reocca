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

class ReoccaActor(interface : String, port : Int, scheduler: ActorRef) extends Actor with Reocca {

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

  def receive = {
    responseScheduler = scheduler
    runRoute(routeWrapper)
  }

}

// Routing embedded in the actor
trait Reocca extends HttpService {
  implicit val system1 = ActorSystem()

  // default logging
  implicit val log = LoggingContext.fromActorRefFactory


//  val cacheMap = HashMap.empty[String, JValue]

  import JsonConversions._

  var responseScheduler : ActorRef = null

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
        get {
          complete(Cache.asView(pathRest))
        } ~ {
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
              routePerMethodBuilder(cacheName, "delete", Cache.entriesByMethod("delete") ) ~
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
    def segmentAppender(segments : PathMatcher0, segment : String) : PathMatcher0 = {
      if (segment.isEmpty)
        segments
      else
        if (segments == null) {
          var xx : PathMatcher0 =  segment
          println("create new segment " + segment)
//       TODO   xx.
          xx
        }
          else {
            print(s":/${segment}:")
            segments / segment
        }
    }
    def buildPath(pathEntry: (TargetEntry, CacheTarget)) : Route = {
      val targetEntry = pathEntry._1
      val cacheTarget = pathEntry._2
      var segments : PathMatcher0 = cacheName
      println(s"segment: ${cacheName}")
      for {
        segment <- cacheTarget.name.split("/")
      } segments = segmentAppender(segments, segment)
      print("<PATH>")
      for {
        segment <- targetEntry.key.split("/")
      } segments = segmentAppender(segments, segment)

      /**
       * post processing the response received back from a forward
       */
      def complementReplacing(eventualResponse: Future[HttpResponse]): ToResponseMarshallable = {
        import scala.concurrent.ExecutionContext.Implicits.global
        Cache.put(eventualResponse, (targetEntry, cacheTarget))
        eventualResponse onComplete {
          case util.Success(hr: HttpResponse) => {
            println("@@@@@@@@@@@@@@@@@@@@@@@@@@  forwarded response received")
            val jrsp = hr.entity.data.asString
            if (cacheTarget.record) {
              println(s"we will update the response to ${jrsp}")
              import org.json4s._
              import org.json4s.jackson.JsonMethods._
              Cache.updateResponse(eventualResponse, parse(jrsp))
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

      /**
       * post processing the response received back from a forward
       */
      def complementAppending(eventualResponse: Future[HttpResponse], pathRemainder : Uri.Path): ToResponseMarshallable = {
        import scala.concurrent.ExecutionContext.Implicits.global
        Cache.put(eventualResponse, (targetEntry, cacheTarget))
        eventualResponse onComplete {
          case util.Success(hr: HttpResponse) => {
            println("@@@@@@@@@@@@@@@@@@@@@@@@@@  forwarded response received")
            val jrsp = hr.entity.data.asString
            if (cacheTarget.record) {
//              println(s"we will update the response to ${jrsp} with remaining key ${pathRemainder.}")
              import org.json4s._
              import org.json4s.jackson.JsonMethods._
              Cache.appendResponse(eventualResponse, pathRemainder.toString(), parse(jrsp))
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

      var url : String = null
      pathPrefix(segments) {
        pathEnd {
          if (cacheTarget.forward) {
            import system1.dispatcher
            var pipeline = pipelining.sendReceive
            def eventualHttpResponse = pipeline {
              Get(cacheTarget.url)
            }
            complete(complementReplacing(eventualHttpResponse))
          } else {
            if (cacheTarget.minSimDelay > 0) {
              (reqC: RequestContext) => {
                responseScheduler ! Scheduled(reqC, System.currentTimeMillis() + cacheTarget.minSimDelay.toLong, targetEntry.response)
              }
            } else
            complete(targetEntry.response)
          }
        } ~ {
          if (cacheTarget.forward) {
                var pathRemainder: Uri.Path = null
              (req : RequestContext) => req.withUnmatchedPathMapped((unmapped)  => {
                pathRemainder = unmapped

                // determine the superfluous part of the segment
                // append that to the cache url
                // forward to that url
                // when complementing that, add the response as a new entry
                var url = cacheTarget.url + unmapped.toString() // todo : exclude requestparamaters
                  println("more UUUUUUUUUUUUUUUUrl : " + url)
                unmapped
              })
            import system1.dispatcher
            var pipeline = pipelining.sendReceive
            def eventualHttpResponse = pipeline {
              println(s"forwarding to ${url}")
              Get(url)
            }
            complete(complementAppending(eventualHttpResponse, pathRemainder))
          } else reject()
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
        case None => reject
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
