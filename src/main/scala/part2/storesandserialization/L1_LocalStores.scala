package part2.storesandserialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object L1_LocalStores extends AbstractPart2App with App{
  val system = ActorSystem("localStoresSystem", ConfigFactory.load().getConfig("localStores"))
  run()
}
