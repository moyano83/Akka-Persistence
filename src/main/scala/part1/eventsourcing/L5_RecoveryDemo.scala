package part1.eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object L5_RecoveryDemo extends App{

  case class Command(contents:String)
  case class Event(contents:String)

  class RecoveryActor  extends PersistentActor with ActorLogging{
    override def persistenceId: String = "RecoveryActor"

    override def receiveRecover: Receive = {
      case Event(content) =>
      if(content.contains("314")) throw new RuntimeException(s"Error recovering on ${content}")
      else log.info(s"Recovered ${content}")
    }

    override def receiveCommand: Receive = {
      case Command(content) => persist(Event(content)){event=>
        log.info(s"Successfully persisted ${event}")
      }
    }

    /**
      * This method is called if an exception is thrown during recovery, after that, the actor is unconditionally stopped
      *
      */
    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error(s"I failed at recovery on ${event.getOrElse("unknown")}")
      super.onRecoveryFailure(cause, event)
    }
  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor])

  /**
    * Stashing commands. All commands sent during recovery are stash, first the actor needs to finish its recovery and
    * then starts processing commands
    */
  for(i<- 1 to 1000){
    recoveryActor ! Command(s"Command ${i}")
  }

  /**
    * Failure during recovery
    */

  Thread.sleep(5000)
  system.terminate()
}
