package part1.eventsourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object L1_PersistentActors extends App {

  // COMMANDS
  case object Shutdown
  case class Invoice(recipient:String, date:Date, amount:Int)
  case class InvoiceBulk(invoices:List[Invoice])

  // EVENTS
  case class InvoiceRecorded(id:Int, recipient:String, date:Date, amount:Int)

  /**
    * Scenario, an accountant for a business that keeps tracks of all the invoices
    */
  class Accountant extends PersistentActor with ActorLogging{
    var latestInvoiceId = 0
    var totalAmount = 0

    // PersistentActors needs to implement the following three methods
    override def persistenceId: String = "Simple-Accountant" // This needs to be unique per actor

    // This is to the normal receive method. On command reception, the pattern is like this:
    // 1 - Create an event to persist it in the store
    // 2 - persist the event
    // 3 - Create a callback that would be called when the event is written
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>

        log.info(s"Received invoice for ${recipient} with amount ${amount}")
        val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount)
        // Persist method takes an event an a callback
        persist(event){ e =>
          //Update the status
          // It is safe to access mutable state here, there is a time gap between the persistence of the message and the
          // call back handling, but akka guarantees that all messages received between this two methods are stashed
          latestInvoiceId += 1
          totalAmount += amount
          log.info(s"Persisted: ${e}")
        }
      /**
        * Persisting multiple events. There is a persist all method
        */
      case InvoiceBulk(invoices) =>
        val invoicesId = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events =
          invoices.zip(invoicesId).map({case (inv, id)=> InvoiceRecorded(id, inv.recipient, inv.date, inv.amount)})
        persistAll(events){e=>
          // This callback is executed after every successfull event insertion
          latestInvoiceId += 1
          totalAmount += e.amount
        }
      // SHUTDOWN of persisting actors: The poison pill and kill messages are handled separately in a separate mailbox so you
      // risk killing the actor before it's actually done persisting if you use that method, the best practice on this regard
      // is to define your own shutdown message
      case Shutdown => context stop self
      case msg => log.warning(s"Unrecognized message ${msg}")
    }

    // handler that would be call on recovery (when all the events would be replayed to get to the actor state it should be)
    // best practice to implement this method is to follow the logic in the persist steps of the receiveCommand method
    override def receiveRecover: Receive = {
      case InvoiceRecorded(id,_,_,amount) =>
        log.info(s"Recovered=>${id} for amount ${amount}")
        latestInvoiceId = id
        totalAmount += amount
    }

    /**
      * Persisting errors
      * The persistent actor provides a couple of methods to deal with errors on persistence
      */
      // This is call if persistent action on the actor fails, the actor would unconditionally be stopped (because we don't
      // know if the persist action succeeded, the actor is in an inconsistent state, therefore it is better to replay de
      // actions to get to a consistent state). Best practice is use backoff supervisor to start the actor after a while.
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Failed to persist ${event} because of ${cause}")
      super.onPersistFailure(cause, event,seqNr)
    }

    // This method would be called if the JOURNAL throws an exception trying to persist, in this case the actor would be
    // resumed, not stopped.
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected ${event} because of ${cause}")
      super.onPersistRejected(cause, event, seqNr)
    }

  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  //If we comment this out and run the application a second time, you can see that the actor retrieves the previously
  // processed events by passing them to the receiveRecover method
  // We need to configure the persistence store or "journal" for our application (file based db for this exercise)_
  for (i <- 1 to 10){
    accountant ! Invoice("LTD Co", new Date(), i*1000)
  }

  val invoices = for (i <- 1 to 10) yield  Invoice("LTD Co", new Date(), i*1000)
  accountant ! InvoiceBulk(invoices.toList)

  /**
    * NOTE: NEVER EVER call persist or persist all from futures, you risk breaking actor encapsulation and you have
    * non-deterministic behaviour that could lead to corrupted state
    */
  accountant ! Shutdown
  Thread.sleep(2000)
  system.terminate()
}
