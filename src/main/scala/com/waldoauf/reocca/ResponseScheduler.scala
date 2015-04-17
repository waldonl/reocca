package com.waldoauf.reocca

import akka.actor._
import org.json4s.JsonAST.JValue
import spray.routing.RequestContext

import scala.collection.mutable

/**
 * Created by Waldo auf der Springe on 4/17/2015.
 */
class DelayedResponseActor() extends Actor with ResponseScheduler {
  override def receive: Receive = tickedResponse
}
case class Scheduled(rc: RequestContext, dueMillis: Long, response: JValue)
trait ResponseScheduler {
  val retries = 3
  implicit val system = ActorSystem()
  val Tick = "tick"
  def schedulePastSlot(count : Integer): Unit = {
    Schedule.schedule(((System.currentTimeMillis() / 100) - count) * 100)
    if (count >= 0) schedulePastSlot(count - 1)
  }
  def tickedResponse: Actor.Receive = {
    case Tick => schedulePastSlot(retries)
    case scheduled:Scheduled => Schedule.put(scheduled )
  }

}
object Schedule {
  val reqByTimeSlot: mutable.Map[Long, List[Scheduled]] = new mutable.HashMap[Long, List[Scheduled]]()
  /**
   * store the request context in a timeslot.
   * returns the timeslot which is a time in millis, with a precision of 1/10 second
   */
  def put(scheduled: Scheduled): Long = {
    val timeSlot : Long = (scheduled.dueMillis / 100) * 100
    println(s"storing delayed request @ ${timeSlot}")
    reqByTimeSlot.get(timeSlot) match {
      case None => reqByTimeSlot.put(timeSlot, List(scheduled))
      case Some(reqList) => {
        reqByTimeSlot.remove(timeSlot)
        reqByTimeSlot.put(timeSlot, scheduled :: reqList)
      }
    }
    timeSlot
  }

  /**
   * sends all due requests to their responder
   */
  def schedule(timeSlot: Long) = {
    import JsonConversions._
    reqByTimeSlot.get(timeSlot) match {
      case Some(scheduledList) => {
        reqByTimeSlot.remove(timeSlot)
        for {
          sched: Scheduled <- scheduledList
        } {
          sched.rc.complete(sched.response)
        }
        print("@")
      }
      case None => null
    }
  }
}