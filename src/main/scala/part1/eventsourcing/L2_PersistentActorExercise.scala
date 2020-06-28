package part1.eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.collection.mutable
import scala.sys.Prop

object L2_PersistentActorExercise extends App {
  /**
    * Persistent actor for voting election. This should:
    * - Keep the citizen who voted to avoid citizens voting twice
    * - The Poll: Map between candidates and number of votes so far
    * - The actor must be able to recover the state if it is shutdown or restarted
    */
  case class Vote(citizenID:String, candidate:String)
  case object Shutdown
  case object Display
  class VotingStation extends PersistentActor with ActorLogging {
    val citizenSet = mutable.HashSet[String]()
    val votes = mutable.HashMap[String,Int]()
    override def receiveRecover: Receive = {
      case vote @ Vote(id, candidate) => {
        citizenSet.add(vote.citizenID)
        votes.put(vote.candidate, (votes.getOrElse(vote.candidate,0) + 1))
      }
    }

    override def receiveCommand: Receive = {
      case vote @ Vote(id, candidate) => receiveVote(vote)
      case Display => votes.foreach({case (candidate, votes)=> log.info(s"CANDIDATE ${candidate}: ${votes} votes")})
      case Shutdown => context stop self
    }
    override def persistenceId: String = "ActorVoting"

    private def receiveVote(vote:Vote):Unit = {
      if (!citizenSet.contains(vote.citizenID)){
        citizenSet.add(vote.citizenID)
        persist(vote){vote =>
          votes.put(vote.candidate, (votes.getOrElse(vote.candidate,0) + 1))
          log.info(s"Persisted vote for candidate ${vote.candidate}")
        }
      }else{
        log.warning(s"Citizen ${vote.citizenID} has already voted")
      }
    }
  }

  val system = ActorSystem("VotingSystem")
  val votingStation = system.actorOf(Props[VotingStation], "Voting_Station")

  votingStation ! Vote("1", "CANDIDATE1")
  votingStation ! Vote("2", "CANDIDATE2")
  votingStation ! Vote("3", "CANDIDATE1")
  votingStation ! Vote("4", "CANDIDATE1")
  votingStation ! Vote("5", "CANDIDATE2")
  votingStation ! Vote("1", "CANDIDATE1")
  votingStation ! Vote("6", "CANDIDATE2")
  votingStation ! Display
  votingStation ! Shutdown

  Thread.sleep(1000)
  system.terminate()

}
