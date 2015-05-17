
package com.waldoauf.reocca
/**
 * Cache has targets has configuration settings and entries
 * entries have entry settings and a response
 * Created by Waldo auf der Springe on 4/9/2015.
 */

import org.json4s
import org.json4s.JsonAST._
import spray.http.{StatusCode, HttpResponse, StatusCodes}

import scala.collection.mutable
import scala.collection.mutable.HashMap
import scala.concurrent.Future

sealed trait UpdateCacheError {def responseStatus : StatusCode}
  case object UnknownField extends UpdateCacheError {val responseStatus = StatusCodes.NotFound}
  case object InvalidValue extends UpdateCacheError {val responseStatus = StatusCodes.Conflict}

class TargetEntry(var key: String = "",
                  var method: String = "get",
                  var reqHeader: String = "",
                  var rspHeader: String = "",
                  var response: JValue = JString("undefined")) {
  override def toString() = s"targetEntry (key: ${key}, method: ${method}},response: ${response})"
  lazy val keyPath = {
    if (key == null || key.isEmpty) "" else {
      if (key.contains('?')) {
        key.split('?')(0)
      } else key
    }
  }
  lazy val keyRequestMap = {
    var rm = Map[String, String]()
    if (key.contains('?')) {
      val splitted = key.split('?')
      val pairs = splitted(1).split('&')
      val req = for (kv <- pairs) {
        val pair = kv.split('=')
        rm = rm + (pair(0) -> pair(1))
      }
    }
    rm
  }

  /**
   * key prepended with '/' if non empty
   */
  def keySegment : String = if (key == null || key.isEmpty) "" else s"/${keyPath}"
}


class EntryListSetting(var entries: List[TargetEntry]) {
  override def toString() = s"cache entries: ${entries}"
}

case class CacheTarget(var name: String = "",
                  var replay: Boolean = true,
                  var forward: Boolean = false,
                  var record: Boolean = false,
                  var minSimDelay: BigInt = 0,
                  var maxSimDelay: BigInt = 0,
                  var keyGeneratorName: String = "",
                  var keyGeneratorParameter: String = "",
                  var url: String = "",
                  var filterNamespace: Boolean = false,
                  var skipHttpHeaders: Boolean = false,
                  var decoratorName: String = "",
                  var entries: EntryListSetting = new EntryListSetting(null))
//  {
//    override def toString() = s"CacheTarget name: ${name}, delay: ${minSimDelay}, url: ${url}, entries: ${entries}"
//  }

object Cache {
  def lexicalSorter(a: String, b: String): Boolean = {
    if (a.length > b.length) {
      if (a.substring(0, b.length) == b) {
        false
      } else {
        a.compareTo(b) > 0
      }
    } else {
      if (b.substring(0, a.length) == b) {
        true
      } else {
        a.compareTo(b) > 0
      }
    }
  }

  def entrySorter(a: (Option[TargetEntry], CacheTarget), b: (Option[TargetEntry], CacheTarget)): Boolean = {
    if (a._2.name == b._2.name) {
      if (a._1 == None) true
      else if (b._1 == None) false
      else lexicalSorter(a._1.get.key, b._1.get.key)
    } else lexicalSorter(a._2.name, b._2.name)
  }

  val responseMap = new mutable.HashMap[Future[HttpResponse], (Option[TargetEntry], CacheTarget)]()

  def updateResponse(eventualResponse: Future[HttpResponse], newResponse: json4s.JValue) = {
    responseMap.remove(eventualResponse) match {
      case Some((Some(targetEntry), _)) => targetEntry.response = newResponse
    }
  }
  def appendResponse(eventualResponse: Future[HttpResponse], pathRemainder: String, newResponse: json4s.JValue) = {
    responseMap.remove(eventualResponse) match {
      case Some((targetEntry, cacheTarget)) => {
        val targetEntryKey = targetEntry match {
          case Some(te) => te.key
          case None => ""
        }
        cacheTarget.entries.entries = new TargetEntry(key = targetEntryKey + pathRemainder, response = newResponse) :: cacheTarget.entries.entries
      }
    }
  }

  def put(eventualResponse: Future[HttpResponse], pathEntry: (Option[TargetEntry], CacheTarget)) = {
    responseMap.put(eventualResponse, pathEntry)
  }

  def remove(eventualResponse: Future[HttpResponse]): Unit = {
    responseMap.remove(eventualResponse)
  }

  type NamedCache = List[CacheTarget]
  val cacheMap = new HashMap[String, NamedCache]()

  def asView(cacheName: String) = {
    cacheMap.get(cacheName) match {
      case Some(namedCache) => {
        for {
          targetEntry <- namedCache
        } yield Map("target" -> targetEntry)
      }
      case _ => null
    }
  }

  def entriesByMethod(method: String): List[(Option[TargetEntry], CacheTarget)] = {
    var result: List[(Option[TargetEntry], CacheTarget)] = Nil
    for {
      cacheName <- cacheMap.keys
    } result = result ::: cacheNameEntriesByMethod(cacheName, method)
    result.sortWith(entrySorter)
  }
  def cacheNameEntriesByMethod(cacheName: String, method: String): List[(Option[TargetEntry], CacheTarget)] = {
    var result: List[(Option[TargetEntry], CacheTarget)] = Nil
    cacheMap.get(cacheName) match {
      case None => None
      case Some(cache: NamedCache) => {
        for {
          cacheTarget: CacheTarget <- cache
          someEntry: Option[TargetEntry] <- (cacheTarget.entries.entries.map(te => Option(te))).::(None)
          if method == "*" || (someEntry match {
            case Some(entry) => entry.method == method
            case otherwise => false
          })
        } result = (someEntry, cacheTarget) :: result
      }
    }
    result.sortWith(entrySorter)
  }


  /**
   * update the field according to the updateparts, which is the argument list passed in the PUT request, comprised of, respecively:<UL>
   * <LE>cache name(potentially multiple strings)</LE>
   * <LE>optional entry key, potentially multiple strings </LE>
   * <LE>http method, in case this list includes an entry key</LE>
   * </UL>
   */
  def updateField(updateParts: Array[String],
                  replay: Option[Boolean], forward: Option[Boolean], record: Option[Boolean],
                  minSimDelay: Option[Int], maxSimDelay: Option[Int],
                  keyGeneratorName: Option[String], keyGeneratorParameter: Option[String],
                  url: Option[String],
                  filterNamespace: Option[Boolean], skipHttpHeaders: Option[Boolean],
                  decoratorName: Option[String], method: Option[String],
                  requestHeader: Option[String], responseHeader: Option[String]
//                  ,response: Option[String]
                   ): Either[UpdateCacheError, CacheTarget] = {
    def updateCacheTarget(cacheTarget: CacheTarget, replay: Option[Boolean], forward: Option[Boolean], record: Option[Boolean], minSimDelay: Option[Int], maxSimDelay: Option[Int], keyGeneratorName: Option[String], keyGeneratorParameter: Option[String], url: Option[String], filterNameSpace: Option[Boolean], skipHttpHeaders: Option[Boolean], decoratorName: Option[String]): Unit = {
      replay match {case Some(rep) => cacheTarget.replay = rep;case otherwise => }
      forward match {case Some(fldVal) => cacheTarget.forward = fldVal;case otherwise => }
      replay match {case Some(fldVal) => cacheTarget.replay = fldVal;case otherwise => }
      record match {case Some(fldVal) => cacheTarget.record = fldVal;case otherwise => }
      minSimDelay match {case Some(fldVal) => cacheTarget.minSimDelay = fldVal;case otherwise => }
      maxSimDelay match {case Some(fldVal) => cacheTarget.maxSimDelay = fldVal;case otherwise => }
      keyGeneratorName match {case Some(fldVal) => cacheTarget.keyGeneratorName = fldVal;case otherwise => }
      keyGeneratorParameter match {case Some(fldVal) => cacheTarget.keyGeneratorParameter = fldVal;case otherwise => }
      url match {case Some(fldVal) => cacheTarget.url = fldVal;case otherwise => }
      filterNameSpace match {case Some(fldVal) => cacheTarget.filterNamespace = fldVal;case otherwise => }
      skipHttpHeaders match {case Some(fldVal) => cacheTarget.skipHttpHeaders = fldVal;case otherwise => }
      decoratorName match {case Some(fldVal) => cacheTarget.decoratorName = fldVal;case otherwise => }
    }
    def updateTargetEntry(targetEntry: TargetEntry, method: Option[String], reqHeader: Option[String],
                          rspHeader: Option[String]
//                          , response: Option[String]
                           ): Unit = {
      method match {case Some(fldVal) => targetEntry.method = fldVal;  case otherwise => }
      reqHeader match {case Some(fldVal) => targetEntry.reqHeader = fldVal; case otherwise => }
      rspHeader match {case Some(fldVal) => targetEntry.rspHeader = fldVal; case otherwise => }
//      response match {
//        case Some(fldVal) => {
//          import org.json4s._
//          import org.json4s.jackson.JsonMethods._
//          try {
//            targetEntry.response = parse(fldVal)
//            Right(targetEntry)
//          } catch {
//            case ioe: Exception => Left(InvalidValue)
//          }
//        }}
    }//
    println(s"in update field url  ${url}")

    val fieldPartList = updateParts.toList
    val cacheName = fieldPartList.head
    val fieldPartEntries = fieldPartList.tail
    val matchingEntry = cacheNameEntriesByMethod(cacheName, "*").find((entry: (Option[TargetEntry], CacheTarget)) => {
      val targetEntryKey = entry._1 match {
        case Some(entry) => entry.keySegment
        case otherwise => "" // or "/" ???
      }
      val targetEntryMethod = entry._1 match {
        case Some(entry) => entry.method
        case otherwise => "/" // never matches
      }
      val entryIdentifierList = s"${entry._2.name}${targetEntryKey}".split('/').toList
      println(s"checking : ${entryIdentifierList} vs ${fieldPartEntries}")
      entryIdentifierList.equals(fieldPartEntries.take(entryIdentifierList.length))  &&
        (entryIdentifierList.length == fieldPartEntries.length || fieldPartEntries.takeRight(1).equals(targetEntryMethod))
    })
    println(s"found match: ${matchingEntry}")
    matchingEntry match {
     case Some((Some(targetEntry), cacheTarget: CacheTarget)) => {
       updateCacheTarget(cacheTarget, replay, forward, record, minSimDelay, maxSimDelay, keyGeneratorName, keyGeneratorParameter,
          url, filterNamespace, skipHttpHeaders, decoratorName)
       updateTargetEntry(targetEntry, method, requestHeader, responseHeader/*, response*/)
       Right(cacheTarget)
     }
     case Some((None, cacheTarget: CacheTarget)) => {
       updateCacheTarget(cacheTarget, replay, forward, record, minSimDelay, maxSimDelay, keyGeneratorName, keyGeneratorParameter,
          url, filterNamespace, skipHttpHeaders, decoratorName)
       Right(cacheTarget)
     }
     case None => Left(UnknownField)
    }
  }

  def putCache(name: String, jCache: JValue) = {
    val cacheTargetList = for {
      JArray(targetList) <- jCache
      targets <- targetList
      JObject(jTarget) <- targets
      JField("target", JObject(jTargetEntries)) <- jTarget
    } yield {
        var newCacheTarget = new CacheTarget()
        for {
          jTargetEntry <- jTargetEntries
        } {
          jTargetEntry match {
            case JField("name", JString(name)) => newCacheTarget.name = name
            case JField("replay", JBool(name)) => newCacheTarget.replay = name
            case JField("forward", JBool(name)) => newCacheTarget.forward = name
            case JField("record", JBool(name)) => newCacheTarget.record = name
            case JField("minSimDelay", JInt(name)) => newCacheTarget.minSimDelay = name
            case JField("maxSimDelay", JInt(name)) => newCacheTarget.maxSimDelay = name
            case JField("keyGeneratorName", JString(name)) => newCacheTarget.keyGeneratorName = name
            case JField("keyGeneratorParameter", JString(name)) => newCacheTarget.keyGeneratorParameter = name
            case JField("url", JString(name)) => newCacheTarget.url = name
            case JField("filterNamespace", JBool(name)) => newCacheTarget.filterNamespace = name
            case JField("skipHttpHeaders", JBool(name)) => newCacheTarget.skipHttpHeaders = name
            case JField("decoratorName", JString(name)) => newCacheTarget.decoratorName = name
            case JField("entries", JArray(entryFields)) => {
              newCacheTarget.entries = new EntryListSetting(
                for {JObject(entry) <- entryFields
                } yield {
                  var targetEntry = new TargetEntry()
                  for {entryField <- entry
                  }
                    entryField match {
                      case JField("key", JString(key)) => targetEntry.key = key
                      case JField("method", JString(key)) => targetEntry.method = key
                      case JField("requestHeader", JString(key)) => targetEntry.reqHeader = key
                      case JField("responseHeader", JString(key)) => targetEntry.rspHeader = key
                      case JField("response", response) => {
                        targetEntry.response = response; println(s"set response of ${targetEntry} to ${response}")
                      }
                      case unmatched => println(s"${unmatched} does not match any entry (key - response")
                    }
                  targetEntry
                })
            }
            case unmatched => println(s"${unmatched} does not match any json target entry (name - entries: ")
          }
        }
        newCacheTarget
      }
    cacheMap.put(name, cacheTargetList)
  }
}
