package part2.storesandserialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object L1_LocalStores extends App{

  val localStores = ActorSystem("localStoresSystem", ConfigFactory.load().getConfig("localStores"))
  val localActor = localStores.actorOf(Props[SimplePersistenceActor], "simplePersistentActor")

  for(i <- 1 to 10) {
    localActor ! s"I love Akka ${i}"
  }
  localActor ! "print"
  localActor ! "snap"

  for(i <- 11 to 20) {
    localActor ! s"I love Akka ${i}"
  }

  Thread.sleep(2000)
  localStores.terminate()
}
