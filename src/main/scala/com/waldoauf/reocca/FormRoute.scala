package com.waldoauf.reocca

/**
 * spray routing without implicit json conversion
 * Created by Waldo on 5/15/2015.
 */

import spray.http.StatusCodes
import spray.routing._

trait FormRoute extends HttpService {
  val routePrefix =
    post {
      path("REOCCA" / Rest) {
        pathRest => {
          formFields("replay".as[Boolean].?,
          "forward".as[Boolean].?,
          "record".as[Boolean].?,
          "minSimDelay".as[Int].?,
          "maxSimDelay".as[Int].?,
          "keyGeneratorName".?,
          "keyGeneratorParameter".?,
          "url".?,
          "filterNameSpace".as[Boolean].?,
          "skipHttpHeaders".as[Boolean].?,
          "decoratorName".?,
          "method".?,
          "requestHeader".?,
          "responseHeader".?
          //,"response".?.as[Jvalue],
          ) {
            println(s"@@@@@in formfields with path ${pathRest}")
            (replay, forward, record, minSimDelay, maxSimDelay,
             keyGeneratorName, keyGeneratorParameter, url, filterNamespace, skipHttpHeaders,
             decoratorName, method, requestHeader, responseHeader) => {
              println(s"####################### run formfields replay: ${replay}, url: ${url}")
              Cache.updateField(pathRest.split('/'), replay, forward, record, minSimDelay, maxSimDelay,
                keyGeneratorName, keyGeneratorParameter, url, filterNamespace, skipHttpHeaders,
                decoratorName, method, requestHeader, responseHeader) match {
                case Left(error) => complete(error.responseStatus)
                case Right(cacheTarget) => complete(StatusCodes.OK)
                case otherwise => reject()
              }
            }
          }

        }
      }
    }
}
