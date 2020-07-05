package part2.storesandserialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object L3_CassandraStorage extends AbstractPart2App with App{
  val system = ActorSystem("cassandraActorSystem", ConfigFactory.load().getConfig("cassandraDemo"))
  run()
}
