package part2.storesandserialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object PostgressStorage extends App{
  val postgressStores = ActorSystem("postgresActorSystem", ConfigFactory.load().getConfig("postgresSQL"))
  val localActor = postgressStores.actorOf(Props[SimplePersistenceActor], "simplePersistentActorPostgres")

  for(i <- 1 to 10) {
    localActor ! s"I love Akka ${i}"
  }
  localActor ! "print"
  localActor ! "snap"

  for(i <- 11 to 20) {
    localActor ! s"I love Akka ${i}"
  }

  Thread.sleep(2000)
  postgressStores.terminate()

}
