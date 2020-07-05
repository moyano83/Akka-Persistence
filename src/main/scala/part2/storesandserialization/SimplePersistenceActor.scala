package part2.storesandserialization

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

class SimplePersistenceActor extends PersistentActor with ActorLogging{
    var nMessages = 0


    override def persistenceId: String = "SimplePersistentActor"

    override def receiveCommand: Receive = {
      case "print" => log.info(s"I have persisted ${nMessages} messages so far")
      case "snap" => saveSnapshot(nMessages)
      case SaveSnapshotSuccess(metadata) => log.info(s"Save snapshot was successful with ${metadata}")
      case SaveSnapshotFailure(metadata, throwable) => log.warning(s"Save snapshot failed: ${throwable.getMessage}")
      case message => persist(message){ _ =>
        log.info(s"I have persisted ${message}")
        nMessages+=1
      }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted => log.info("Recovery done")
      case SnapshotOffer(metadata, payload:Int) =>
        log.info(s"Recovered snapshot ${payload}")
        nMessages = payload
      case msg =>
        log.info(s"Recovered ${msg}")
        nMessages+=1
    }
  }