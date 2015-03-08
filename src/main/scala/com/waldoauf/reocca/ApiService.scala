package com.waldoauf.reocca

// the service, actors and paths

import akka.actor.Actor

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

  val resources = HashMap.empty[String, JObject]
  var counter = 0L

  import JsonConversions._

  def paths(methName: String, pathObjects: JValue): List[List[JField]] = {

  println(s"paths for ${methName} : ${pathObjects}")
  for {
    JObject(child) <- pathObjects
    JField("method", JString(meth)) <- child
    if meth.equals(methName)
  } yield child
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

  def buildRoute(cacheStr : String) = {
    getterBuilder(paths("get", parse(cacheStr)))
  }

  val gets = paths("get", parse(initCache))
  val posts = paths("post", parse(initCache))
  val route = buildRoute(initCache) // getterBuilder(gets)

  def getterBuilder(gets: List[List[JField]]) : Route = {
    def mkPath(cacheEntry: List[JField]) : Route = {
      var name = ""
      for {
        JField("name", JString(jname)) <- cacheEntry
      } name = jname
      var response = parse("[]")
      for {
        JField("response", jresponse) <- cacheEntry
      } response = jresponse
      println(s"creating route for path ${name} and response ${response}")
      path(name) {
        complete(response)
      }
    }

    def recurGets(gets: List[List[JField]], acc : Option[Route]) : Route = {
      if (gets.isEmpty) {println(s"route complete ${acc.get}")
        acc.getOrElse(complete("invalid cache"))} else {
        val innerRoute = mkPath(gets.head)
        val result = acc match {
          case None => new Some(innerRoute)
          case Some(outer: Route) => new Some(outer ~ innerRoute)
        }
        recurGets(gets.tail, result)
      }
    }
    recurGets(gets,  None : Option[Route] )
  }

  def build(json : JValue) : Route = {
    val resp = (json \\ "key").values.toString
    println("resp: " + resp)
    get{_.complete(resp)} }
    /* */
//  val route2 = {
//    path("orders") {
//      authenticate(BasicAuth(realm = "admin area")) { user =>
//        get {
//          cache(simpleCache) {
//            encodeResponse(Deflate) {
//              complete {
//                // marshal custom object with in-scope marshaller
//                getOrdersFromDB
//              }
//            }
//          }
//        } ~
//          post {
//            // decompresses the request with Gzip or Deflate when required
//            decompressRequest() {
//              // unmarshal with in-scope unmarshaller
//              entity(as[Order]) { order =>
//                // transfer to newly spawned actor
//                detach() {
//                  complete {
//                    // ... write order to DB
//                    "Order received"
//                  }
//                }
//              }
//            }
//          }
//      }
//    } ~
//      // extract URI path element as Int
//      pathPrefix("order" / IntNumber) { orderId =>
//        pathEnd {
//          // method tunneling via query param
//          (put | parameter('method ! "put")) {
//            // form extraction from multipart or www-url-encoded forms
//            formFields('email, 'total.as[Money]).as(Order) { order =>
//              complete {
//                // complete with serialized Future result
//                (myDbActor ? Update(order)).mapTo[TransactionResult]
//              }
//            }
//          } ~
//            get {
//              // JSONP support
//              jsonpWithParameter("callback") {
//                // use in-scope marshaller to create completer function
//                produce(instanceOf[Order]) { completer => ctx =>
//                  processOrderRequest(orderId, completer)
//                }
//              }
//            }
//        } ~
//          path("items") {
//            get {
//              // parameters to case class extraction
//              parameters('size.as[Int], 'color ?, 'dangerous ? "no")
//                .as(OrderItem) { orderItem =>
//                // ... route using case class instance created from
//                // required and optional query parameters
//              }
//            }
//          }
//      } ~
//      pathPrefix("documentation") {
//        // cache responses to GET requests
//        cache(simpleCache) {
//          // optionally compresses the response with Gzip or Deflate
//          // if the client accepts compressed responses
//          compressResponse() {
//            // serve up static content from a JAR resource
//            getFromResourceDirectory("docs")
//          }
//        }
//      } ~
//      path("oldApi" / Rest) { pathRest =>
//        redirect("http://oldapi.example.com/" + pathRest, StatusCodes.MovedPermanently)
//      }
//  }
//}
/*
*/

}
