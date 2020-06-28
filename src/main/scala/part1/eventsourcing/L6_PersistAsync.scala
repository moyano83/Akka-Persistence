package part1.eventsourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object L6_PersistAsync extends App{
  /**
    * Persist async is design for high-throughput cases and it relax event ordering guarantees
    */

  case class Command(content:String)
  case class Event(content:String)

  object CriticalStreamProcessor {
    def props(eventAggregator:ActorRef) = Props(new CriticalStreamProcessor(eventAggregator))
  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def persistenceId: String = "CriticalStreamActor"

    override def receiveRecover: Receive = {
      case msg => log.info(s"Recovered $msg")
    }

    override def receiveCommand: Receive = {
      case Command(content) => {
        log.info(s"Processor, processing command ${content}")
        eventAggregator ! s"Processing content: ${content}"
        persistAsync(Event(content)){e=>
          eventAggregator ! e
        }
        // simulation of computation
        val processedContent = content + "_processed"
        persistAsync(Event(processedContent)){e=>
          eventAggregator ! e
        }
      }
    }
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case msg => log.info(s"Aggregating messages: $msg")
    }
  }

  val system = ActorSystem("PersistAsyncDemo")
  val aggregator = system.actorOf(Props[EventAggregator], "Aggregator")
  val processor = system.actorOf(CriticalStreamProcessor.props(aggregator), "StreamProcessor")

  // With the normal receiveCommand implementation using persist, every process involving the command 1 will be processed
  // before the command 2. But with persistAsync the messages are intermingled. This is because in the persist method,
  // there is a gap in time beetween the persist action and the handler execution in which any message received is stashed
  // and processed after (guarantees order), but with persistAsync does not stash incoming messages but processes them,
  // therefore the order is not guaranteed. between messages receives, but it is guaranteed inside the method.
  // If you don't need to keep the order of events but need high throughput, then use persistAsync, but do not use it if
  // for example you need to recover an state for your actor (because it is non-deterministic).
  processor ! Command("Command1")
  processor ! Command("Command2")

  Thread.sleep(2000)
  system.terminate()
}