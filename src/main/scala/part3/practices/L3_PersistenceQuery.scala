package part3.practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.ConfigFactory

import scala.util.Random

object L3_PersistenceQuery extends App {
  /**
    * Akka provides a solution to 'Query' the journal, this allows us to:
    * - Select persistence IDs
    * - Select events by persistence IDs
    * - Select events across persistence IDs, by Tags
    *
    * This is useful for example for:
    * - Find Which persistence actors are active at a particular time
    * - Recreate older states
    * - Track how you arrive at the current state
    * - Perform data aggregation or data processing on events in the entire store
    */


  val system = ActorSystem("persistenceQueryDemo", ConfigFactory.load.getConfig("persistenceQuery"))

  //To access the persistence query api we need what is call a Read Journal
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // This returns all the persistence Ids that are available in the journal
  // This returns a Source[String, NotUsed] which belongs to the akka.streams package, and represents an infinite
  // collection of String objects
  val persistenceIds = readJournal.persistenceIds()

  // We need to put in context an implicit of type Materializer, which second parameter is the actor system
  implicit val materializer:Materializer = ActorMaterializer()(system)
  //  This persistenceIds collection is live updated, so if another actor writtes in the journal, this foreach will
  //  receive the new Id on the fly
  persistenceIds.runForeach(id => println(id))
  // If instead you want a finite set of id, you can call currentPersistenceIds which returns a Source containing the ids
  // available at the time of the call, and then the stream will be closed
  val finitePersistenceIds = readJournal.currentPersistenceIds()


  // It is possible to recover all the events by persistenceId within a range of sequence Nr. This as with the persistence
  // Ids query shown before, is updated in real time, so any new events will be processed once received
  val events = readJournal.eventsByPersistenceId("Coupon_Manager", 0, Long.MaxValue)
  events.runForeach(println)
  // if you don't want it to be updated, use
  val currentEvents = readJournal.currentEventsByPersistenceId("Coupon_Manager", 0, Long.MaxValue)
  currentEvents.runForeach(println)


  // It is possible also to get events by tags, which allows to query events across multiple persistence Ids
  val genres = Array("pop", "rock", "hip hop", "jazz", "disco")
  case class Song(artist:String, title:String, genre:String)
  // Command
  case class Playlist(songs:List[Song])
  // Event
  case class PlaylistPurchase(id:Int, songs:List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging{
    var latestPlaylistId = 0

    override def persistenceId: String = "music-store-checkout"

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        // In order to be able to query events by tag, we need to tag the event before it reaches the Journal, this is
        // done in an event adapter
        persist(PlaylistPurchase(latestPlaylistId, songs)){ e =>
          log.info(s"Purchased ${songs}")
          latestPlaylistId+=1
        }
    }

    override def receiveRecover: Receive = {
      case PlaylistPurchase(id,_) => {
        log.info(s"Recovered playlist ${id}")
        latestPlaylistId = id
      }
    }
  }

  class MusicStoreEventAdapter extends WriteEventAdapter{
    override def manifest(event: Any): String = "Music-store"

    override def toJournal(event: Any): Any = event match{
      case PlaylistPurchase(_, songs) =>
        val genres = songs.map(song => song.genre).toSet
        //Tagged receives the event and all the tag. It is a wrapper of an event with has a set of strings attached to it
        Tagged(event, genres)
      case event => event
    }
  }

  val actor = system.actorOf(Props[MusicStoreCheckoutActor])
  val r = new Random()
  for (_ <- 1 to 10){
    val maxSong = r.nextInt(5)
    val songs = for(i <- 1 to maxSong) yield {
      val genre = genres(r.nextInt(5))
      Song(s"Artist${i}", s"song${i}", genre)
     }
    actor ! Playlist(songs.toList)
  }

  // Again this is updated in real time, use currentEventsByTag for a finite stream of songs
  val rockPlaylist = readJournal.eventsByTag("rock", Offset.noOffset)
  rockPlaylist.runForeach(ev => println(s"Found a playlist with a rock song ${ev}"))

  Thread.sleep(2000)
  system.terminate()
}
