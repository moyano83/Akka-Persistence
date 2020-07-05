package part3.practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.util.Random


object DomainModel {
  case class User(id: String, email: String)
  case class Coupon(code: String, promotionAmount: Int)
  //Comands
  case class ApplyCoupon(coupon: Coupon, user: User)
  //Events
  case class CouponApplied(code: String, user: User)

}
// This is the definitions that would end up in the Journal, we need to create an adapter after to transform types
object DataModel{
  case class WrittenCouponApplied(code:String, userId:String, userEmail:String)
}

class ModelAdapter extends EventAdapter{
  import DomainModel._
  import DataModel._

  override def manifest(event: Any): String = "CouponModelAdapter"

  // Actor -> toJournal -> Serializer -> Journal
  override def toJournal(event: Any): Any = {
    event match {
      case event @ CouponApplied(code, user) => {
        println(s"Converting domain model event ${event} to DataModel")
        WrittenCouponApplied(code, user.id, user.email)
      }
    }
  }

  // journal -> Serializer -> fromJournal -> Actor
  override def fromJournal(event: Any, manifest: String): EventSeq = {
    event match {
      case event @ WrittenCouponApplied(code, id, email) => {
        println(s"Converting Data model ${event} to domain model")
        EventSeq.single(CouponApplied(code, User(id, email)))
      }
    }
  }
}

object L2_DetachingModels extends App{
  /**
    * This class is going to keep track which coupon discounts have been applied
    */
  class CouponManager extends PersistentActor with ActorLogging{
    import DomainModel._
    val coupons:mutable.Map[String, User] = mutable.HashMap[String, User]()

    override def persistenceId: String = "Coupon_Manager"

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if(!coupons.contains(coupon.code)){
          persist(CouponApplied(coupon.code, user)) { ev =>
            coupons.put(coupon.code, user)
            log.info(s"Applied coupon ${coupon.code} for user ${user}")
        }
      }
    }

    override def receiveRecover: Receive = {
      case CouponApplied(code, user) => {
        log.info(s"Recovered ${code} for ${user}")
        coupons.put(code, user)
      }
    }
  }

  val system = ActorSystem("DetachingModels", ConfigFactory.load().getConfig("detachingModels"))
  val couponManager = system.actorOf(Props[CouponManager], "couponManager")
  import DomainModel._

  for (i <- 1 to 5) {
    val coupon = Coupon(s"DISCOUNT_COUPON_${i}", Random.nextInt(100))
    val user = User(s"${i}", s"user_${i}@test.com")
    couponManager ! ApplyCoupon(coupon, user)
  }

  /**
    * We have effectively detached the data persisted from the domain model, this is good because it eases the schema
    * evolution, there is less touch points:
    * - If model changes, only fromJournal needs to be amended
    * - If the DataModel changes we need to change toJournal
    * - The actor remains untouchedce
    */

  Thread.sleep(2000)
  system.terminate()
}
