package part1.eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object L4_Snapshots extends App{

  /**
    * Long live persist actors might take a lot of time to recover in case a failure, for that we have snapshots or
    * checkpoints
    */
  // Implement a whatsapp like messaging app
  object Chat{
    def props(owner:String, contact:String) = Props(new Chat(owner, contact))
  }
  case class SentMessage(contents:String)
  case class ReceivedMessage(contents:String)

  case class ReceivedMessageRecord(id:Int, content:String)
  case class SentMessageRecord(id:Int, content:String)

  class Chat(owner:String, contact:String) extends PersistentActor with ActorLogging{

    var currentMessageId = 0
    // We assume we can only hold a limited number of messages in memory
    val maxMessages = 10
    val lastMessages = new mutable.Queue[(String, String)]()
    var commandsWithoutCheckpoint = 0

    override def receiveCommand: Receive = {
      case ReceivedMessage(content) => persist(ReceivedMessageRecord(currentMessageId, content)){msg=>
        process(msg.content, contact)
        maybeCheckpoint()
      }
      case SentMessage(content) =>  persist(SentMessageRecord(currentMessageId, content)){msg=>
        process(msg.content, owner)
        maybeCheckpoint()
      }
    }


    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, content) =>
        log.info(s"Recovered received ${id} with content ${content}")
        process(content, contact)
        currentMessageId = id
      case SentMessageRecord(id, content) =>
        log.info(s"Recovered sent ${id} with content ${content}")
        process(content, owner)
        currentMessageId = id
      // As we are doing snapshot, we need to indicate how to recover from snapshot, this snapshot offer is how akka
      // passes its value to the receive. With this in place, akka would recover the state of the system since the last
      // snapshot, not the entire history
      case SnapshotOffer(metadata, entity) => {
        log.info("Processing Snapshot offer")
        val newQueue = entity.asInstanceOf[mutable.Queue[(String, String)]]
        newQueue.foreach(lastMessages.enqueue(_))
      }
      //Snapshot related messages
      case SaveSnapshotSuccess(metadata) => log.info(s"Snapshot succeeded with ${metadata}")
      case SaveSnapshotFailure(metadata, throwable) => log.error(s"Snapshot failure with ${metadata}", throwable)
    }

    override def persistenceId: String = s"${owner}-${contact}-chat"

    private def process(content: String, sender:String) = {
      log.info(s"Message: ${content}")
      if (lastMessages.size >= maxMessages) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((owner, content))
      currentMessageId += 1
    }
    def maybeCheckpoint() = {
      commandsWithoutCheckpoint += 1
      if(commandsWithoutCheckpoint == maxMessages){
        log.info("Saving checkpoint")
        //The following methods persist the whole instance passed to it
        saveSnapshot(lastMessages)
        // The above is an asyncronous event, on completeion, this actor will receive a SaveSnapshotSucceed or
        // SaveSnapshotFailure message
        lastMessages.clear()
        commandsWithoutCheckpoint = 0
      }
    }
  }

  val system = ActorSystem("ChatAppSystem")
  val chat = system.actorOf(Chat.props("Jorge1", "Martin2"))

  // This is potentially very slow to recover, so we can persist the entire status with snapshot
  for(i<- 1 to 1000){
    chat ! SentMessage(s"Akka rocks $i")
    chat ! ReceivedMessage(s"Akka rules $i")
  }

  Thread.sleep(10000)
  system.terminate()
}
