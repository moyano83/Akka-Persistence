package part2.storesandserialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object L2_PostgressStorage extends AbstractPart2App with App{
  override val system = ActorSystem("postgresActorSystem", ConfigFactory.load().getConfig("postgresSQL"))
  run()
}
