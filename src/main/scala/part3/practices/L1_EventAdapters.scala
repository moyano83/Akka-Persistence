package part3.practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object L1_EventAdapters extends App {
  /**
    * We are designing an online store for acoustic guitars, first we run the code to persist the guitars, and then we add
    * a field in the Guitar class, so the schema is modified. If we do this without modifying anything, the recover will
    * fail since the schema is different. Possible solutions:
    *
    *   1 - Don't modify the object but create a GuitarV2, and in recover, handle Guitar and Guitar2 as case messages to
    *   process. This is not ideal because for every schema change we need to create a new event and handle it.
    *   2 - Create an EventAdapter, which would read the guitar event1 and transform it to the latest version. We use the
    *   ReadEventAdapter trait to do this, which allows us to transform a type stored in the journal to other type
    */
  //Data Structures
  case class Guitar(id:String, model:String, make:String)
  case class GuitarV2(id:String, model:String, make:String, guitarType:String)
  //command
  case class AddGuitar(guitar: Guitar, quantity:Int)
  case class AddGuitarV2(guitar: GuitarV2, quantity:Int)
  //Events
  case class GuitarAdded(id:String, model:String, make:String, quantity:Int)
  case class GuitarAddedV2(id:String, model:String, make:String, guitarType:String, quantity:Int)

  class InventoryManager extends PersistentActor with ActorLogging{
    val inventory: mutable.Map[GuitarV2, Int] = mutable.HashMap[GuitarV2,Int]()

    override def persistenceId: String = "Guitar-Inventory-Manager"

    override def receiveCommand: Receive = {
      case AddGuitarV2(guitar @ GuitarV2(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, guitarType, quantity)) { ev =>
          addGuitarInventory(guitar, quantity)
          log.info(s"Guitar Added with ${id}, ${model}, ${quantity}")
        }
      case "print" => log.info(s"Current inventory is ${inventory}")
    }

    override def receiveRecover: Receive = {
      case GuitarAddedV2(id, model, make, guitarType, quantity) =>
        addGuitarInventory(GuitarV2(id,model,make, guitarType), quantity)
        log.info(s"Guitar recovered with ${id}, ${model}, ${quantity}")
    }

    private def addGuitarInventory(guitar:GuitarV2, quantity:Int) = {

      inventory.put(guitar, inventory.getOrElse(guitar, 0) + quantity)
    }
  }


  class GuitarReadEventAdapter extends ReadEventAdapter{
    /**
      * journal -> Serializer     -> EventAdapter: Type[A] to Type[B]
      * (Bytes)    (GuitarAdded)     (GuitarAddedV2)
     **/
    override def fromJournal(event: Any, manifest: String): EventSeq = {
      event match {
        case GuitarAdded(id, model, make, quantity) => {
          EventSeq.single(GuitarV2(id, model, make, "acoustic"), quantity)
        }
        case _ @ event => EventSeq.single(event)
      }
    }
  }

  /**
    * There is also a WriteEventAdapter usually used for backwards compatibility, which flow is:
    * actor -> Write event Adapter -> Serializer -> journal
    */

  val system = ActorSystem("EventAdapters", ConfigFactory.load().getConfig("eventAdapters"))
  val inventoryManager = system.actorOf(Props[InventoryManager],  "inventoryManager")
  val guitars = for(i<-1 to 10) yield GuitarV2(s"${i}", s"Hacker${i}", s"Maker${i}", "acoustic")

  guitars.foreach(guitar => inventoryManager ! AddGuitarV2(guitar, 3))

  inventoryManager! "print"

  Thread.sleep(2000)
  system.terminate()
}
