package part1.eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object L5_RecoveryDemo extends App{

  case class Command(contents:String)
  case class Event(contents:String)
  case class EventWithId(id:Int, contents:String)

  class RecoveryActor  extends PersistentActor with ActorLogging{
    override def persistenceId: String = "RecoveryActor"

    override def receiveRecover: Receive = {
      case Event(content) =>
      if (this.recoveryFinished) log.info("Recovery is not finished yet")
      if(content.contains("314")) throw new RuntimeException(s"Error recovering on ${content}")
      else log.info(s"Recovered ${content}")
    }

    override def receiveCommand: Receive = {
      /**
        * Recovery status: two methods to know thee status of the recovery recoveryFinished and recoveryRunning, which
        * are useful if a block of code is shared, between receive command and receive recover (because command and
        * recover run sequentially). A more useful thing is to receive a message when recover is finished, which is done
        * with the object
        */
      case Command(content) => persist(Event(content)){event=>
        if (this.recoveryFinished) log.info("Recovery is not finished yet")
        else log.info(s"Successfully persisted ${event}")
      }
    }

    /**
      * This method is called if an exception is thrown during recovery, after that, the actor is unconditionally stopped
      * You can debug your recovery by customizing the recovery method like below
      */
    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      super.onRecoveryFailure(cause, event)
    }

    /**
      * This returns the configuration of the recovery of this actor
      */
    override def recovery: Recovery = {
      // Do not persist more events after a customize recovery, specially if it is incomplete
      // Parameters here can be:
      // - toSequenceNr: Specified to up to which sequence number should recover.
      // Recovery(toSequenceNr = 100)
      // - fromSnapshot: You need to pass a snapshot selection criteria object, which specifies since when you want to
      // select events
      // Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
      // - replayMax
      Recovery.none // The effect of this is that the recovery won't take place
    }
  }

  class RecoveryActorStateFull  extends PersistentActor with ActorLogging{

    override def persistenceId: String = "RecoveryActorStateFull"
    override def receiveCommand: Receive = {
      log.info("Starting from 0")
      newReceive(0)
    }

    def newReceive(lastId:Int):Receive = {
      case Command(content) => persist(EventWithId(lastId, content)) { event =>
        log.info(s"Successfully persisted ${event}")
        context.become(newReceive(lastId + 1))
      }
    }

    /**
      * You can't change the receiveRocover handle with context become. It is always the one you define in the
      * receiveRecover method. What context become does here is that it changes the handler of the receiveCommand method
      * and uses whichever you put in context.become
      */
    override def receiveRecover: Receive = {
      case RecoveryCompleted => log.info("Finished recovery routine")
      case EventWithId(id, content) =>
        log.info(s"Recovered ${content} for id ${id}")
        context.become(newReceive(id + 1))
    }
  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor])
  val recoveryActorStateFull = system.actorOf(Props[RecoveryActorStateFull])

  /**
    * Stashing commands. All commands sent during recovery are stash, first the actor needs to finish its recovery and
    * then starts processing commands
    */
  for(i<- 1 to 1000){
    //recoveryActor ! Command(s"Command ${i}")
    recoveryActorStateFull ! Command(s"Command ${i}")
  }

  /**
    * Failure during recovery
    */

  Thread.sleep(5000)
  system.terminate()
}
