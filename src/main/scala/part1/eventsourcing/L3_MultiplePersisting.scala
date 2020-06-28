package part1.eventsourcing

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object L3_MultiplePersisting extends App{
  /**
    * We want to persist multiple type of events and persist them
    */
  // Extends the previous account example, the account would persist two types of events:
  // 1 - The invoice
  // 2 - A tax record

  // COMMAND
  object DiligentAccountant {

    case class Invoice(recipient: String, date: Date, amount: Int)

    // EVENTS
    case class TaxRecord(taxId: String, recordId: Int, date: Date, amount: Int)

    case class InvoiceRecord(invoiceRecordId: Int, recipient: String, date: Date, amount: Int)

    def props(taxID:String, taxAuthority:ActorRef) = Props(new DiligentAccountant(taxID, taxAuthority))
  }
  class DiligentAccountant(taxId:String, taxAuthority:ActorRef) extends PersistentActor with ActorLogging {
    import DiligentAccountant._

    var latestTaxRecordId = 0
    var latestInvoiceRecordId = 0
    override def receiveRecover: Receive = {
      case event => log.info(s"Recovered: ${event}")
    }

    override def receiveCommand: Receive = {
      //We need two calls to persist an Invoice record and a tax record
      case invoice @ Invoice(recipient, date, amount) =>
        // The message ordering taxrecord and then incoicerecord is guaranteed
        // This is because persist is also implemented with actors (something like journal ! Record)
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount/3)){ record => // persist1
          taxAuthority ! record
          latestTaxRecordId +=1
          /**
            * NESTED PERSISTENCE: The order in which the tax Authority would receive the messages is the following:
            * persist1, persist3, persist2, persist4
            */
          persist("I hereby declare this record to be accurate and complete"){declaration => //persist2
            taxAuthority ! declaration
          }
        }
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)){record => //persist3
          taxAuthority ! invoice
          latestInvoiceRecordId += 1
          persist("I hereby declare this invoice to be accurate and complete"){declaration => //persist4
            taxAuthority ! declaration
          }
        }
    }

    override def persistenceId: String = "Diligent-Accountant"
  }

  class TaxAuthority extends Actor with ActorLogging{
    override def receive: Receive = {
      case msg => log.info(s"Received ${msg}")
    }
  }

  val system = ActorSystem("DiligentAccountantSystem")
  val taxAuthority  = system.actorOf(Props[TaxAuthority], "HMRC")
  val accountant = system.actorOf(DiligentAccountant.props("UK_822", taxAuthority))

  import DiligentAccountant._
  accountant ! Invoice("Cooperative Co", new Date(), 1000)
  // It is guaranteed that all the persist calls from the message above are processed before the persist of the call below,
  // This is because the messages from below are stashed until all the messages from above are processed
  accountant ! Invoice("Limited Co", new Date(), 2000)


  Thread.sleep(1000)
  system.terminate()
}
