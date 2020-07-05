package part2.storesandserialization

import akka.actor.{ActorSystem, Props}

abstract class AbstractPart2App {
  val system:ActorSystem
  lazy val actor = system.actorOf(Props[SimplePersistenceActor], "simplePersistentActor")



  def run()={

    for(i <- 1 to 10) {
      actor ! s"I love Akka ${i}"
    }
    actor ! "print"
    actor ! "snap"

    for(i <- 11 to 20) {
      actor ! s"I love Akka ${i}"
    }

    Thread.sleep(5000)
    system.terminate()

  }
}
