package com.waldoauf.reocca

// the service, actors and paths

import akka.actor._
import com.waldoauf.reocca.Cache.cacheMap
import spray.client.pipelining
import spray.client.pipelining._
import spray.http._
import spray.httpx.Json4sJacksonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.routing._
import spray.util._

import scala.concurrent.Future

// .native.JsonMethods._

import org.json4s._

/* Used to mix in Spray's Marshalling Support with json4s */
object JsonConversions extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats: Formats = DefaultFormats
}

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
trait Reocca extends FormRoute  {
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
        } ~ get {
          complete(Cache.asView(pathRest))
        }
      }
    }



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
      case Some(route) => routePrefix ~ route ~ routeAppendix ~ complete(StatusCodes.NotFound, cacheMap)
    }
  }

  def routePerMethodBuilder(cacheName : String, methName : String, pathList:List[(Option[TargetEntry], CacheTarget)]) : Route = {
    def segmentAppender(segments : PathMatcher0, segment : String) : PathMatcher0 = {
      if (segment.isEmpty)
        segments
      else {
        print(s":/${segment}:")
        segments / segment
      }
    }
    def buildPath(pathEntry: (Option[TargetEntry], CacheTarget)) : Route = {
      val targetEntry = pathEntry._1
      val cacheTarget = pathEntry._2
      var segments : PathMatcher0 = cacheName
      println(s"segment: ${cacheName}")
      for {
        segment <- cacheTarget.name.split("/")
      } segments = segmentAppender(segments, segment)
      print("<PATH>")
      for {
        segment <- targetEntry match {
          case Some(te) => te.keyPath.split("/")
          case otherwise => Array("") // or "/" ???
        }
      } segments = segmentAppender(segments, segment)

      /**
       * replace the response of an existing path entry with the one received back from a forward
       */
      def complementReplacing(eventualResponse: Future[HttpResponse]): ToResponseMarshallable = {
        import scala.concurrent.ExecutionContext.Implicits.global

        Cache.put(eventualResponse, (targetEntry, cacheTarget))
        eventualResponse onComplete {
          case util.Success(hr: HttpResponse) => {
            println("@@@@@@@@@@@@@@@@@@@@@@@@@@  forwarded replaceable response received")
            val jrsp = hr.entity.data.asString
            if (cacheTarget.record) {
              println(s"we will update the response to ${jrsp}")
              import org.json4s._
              import org.json4s.jackson.JsonMethods._
              Cache.updateResponse(eventualResponse, parse(jrsp))
              route = buildRoute()
            }
          }
          case util.Success(somethingElse) =>  {
            println(s"we got something else: ${somethingElse}")
            Cache.remove(eventualResponse)
          }
          case util.Failure(error) => {
            println("we got an error")
          }
          case otherwise => StatusCodes.ServerError
        }
        eventualResponse
      }

      /**
       * create a new path entry to store the response received back from a forward
       */
      def complementAppending(eventualResponse: Future[HttpResponse], pathRemainder : String): ToResponseMarshallable = {
        import scala.concurrent.ExecutionContext.Implicits.global
        Cache.put(eventualResponse, (targetEntry, cacheTarget))
        eventualResponse onComplete {
          case util.Success(hr: HttpResponse) => {
            println(s"@@@@@@@@@@@@@@@@@@@@@@@@@@  forwarded appendable response received ${cacheTarget.name}= =${if (targetEntry.isDefined) targetEntry.get.keySegment}= =${pathRemainder}")
            val jrsp = hr.entity.data.asString
            if (cacheTarget.record) {
              //              println(s"we will update the response to ${jrsp} with remaining key ${pathRemainder.}")
              import org.json4s._
              import org.json4s.jackson.JsonMethods._
              Cache.appendResponse(eventualResponse, pathRemainder, parse(jrsp))
              route = buildRoute()
            }
          }
          case util.Success(somethingElse) => {
            Cache.remove(eventualResponse)
            println(s"we got something else: ${somethingElse}")
          }
          case util.Failure(error) => {
            Cache.remove(eventualResponse)
            println("we got an error")
          }
        }
        eventualResponse
      }
      /**
       * This optional target entry matches with the complete request path, and when forward and replay are on, the entry will be updated.
       * If the target entry is None, the cache target itself matches.
       */
      def targetEntryFound: Route = {
        if (cacheTarget.forward) {
          import system1.dispatcher
          var pipeline = pipelining.sendReceive
          def eventualHttpResponse = pipeline {
            Get(cacheTarget.url)
          }
          targetEntry match {
            case Some(te) => complete(complementReplacing(eventualHttpResponse))
            case None => complete(complementAppending(eventualHttpResponse, ""))
          }
        } else {
          if (targetEntry.isDefined) {
            if (cacheTarget.minSimDelay > 0 ) {
              (reqC: RequestContext) => {
                responseScheduler ! Scheduled(reqC, System.currentTimeMillis() + cacheTarget.minSimDelay.toLong, targetEntry.get.response)
              }
            } else
              complete(targetEntry.get.response)
          } else {
            reject()
          }
        }
      }
      /**
       * This optional targetEntry matches, but the request is too specific and triggers a new entry to be added to the cache, when forward is true
       */
      def targetEntryMissed = {
        if (cacheTarget.forward) {
          var pathRemainder: Uri.Path = null
          (req : RequestContext) => {req.withUnmatchedPathMapped((unmapped)  => {
            pathRemainder = unmapped
            unmapped
          }
          )
            val targetEntryKey = targetEntry match {
              case Some(te) => te.keySegment
              case otherwise => ""
            }
            var url = cacheTarget.url + targetEntryKey + pathRemainder.toString() // includes optional requestparamaters
            import system1.dispatcher
            var pipeline = pipelining.sendReceive
            def eventualHttpResponse = pipeline {
              Get(url)
            }
            req.complete(complementAppending(eventualHttpResponse, pathRemainder.toString()))
          }
        } else reject()
      }
      def isPartOf(part: Map[String, String], whole: Map[String, String]) : Boolean = {
        part.forall{case (k,v) => Some(v) == whole.get(k)}
      }
      var url : String = null
      val emptyKeyRequestMap : Map[String, String] =  Map()
      val targetEntryKeyRequestMap : Map[String, String] = targetEntry match {
        case Some(te: TargetEntry) => te.keyRequestMap
        case otherwise => emptyKeyRequestMap
      }
      pathPrefix(segments) {
        pathEnd {
          parameterMap { reqPrmsMap => {
            if (isPartOf(reqPrmsMap, targetEntryKeyRequestMap)) {
              if (isPartOf(targetEntryKeyRequestMap, reqPrmsMap)) targetEntryFound
              else targetEntryMissed
            } else {
              println(s"pathend: incoming request ${reqPrmsMap} not part of this cache entry ${targetEntryKeyRequestMap}")
              reject()
            }
          }}
        } ~ {
          parameterMap { reqPrmsMap => {
              if (isPartOf(targetEntryKeyRequestMap, reqPrmsMap)) targetEntryMissed
              else {
                println(s"pathpref: this cache entry ${targetEntryKeyRequestMap} does not match incoming request ${reqPrmsMap}")
                reject()
              }
            }
          }
        }
      }
    }
    def buildMethod(methName: String) : Directive0 = methName match {
      case "post" => method(HttpMethods.POST)
      case "delete" => method(HttpMethods.DELETE)
      case "put" => method(HttpMethods.PUT)
      case _ => method(HttpMethods.GET)
    }

    def connectPaths(pathList: List[(Option[TargetEntry], CacheTarget)], acc : Option[Route]) : Route = {
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
