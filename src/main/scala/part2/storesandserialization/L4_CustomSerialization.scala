package part2.storesandserialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory
import spray.json
import spray.json.DefaultJsonProtocol
//Commands
case class RegisterUser(email:String,name:String)
//Event
case class UserRegister(id:Int, email:String, name:String)

class UserRegistrationActor extends PersistentActor with ActorLogging{
  var currentId =0
  override def persistenceId: String = "user-registration"

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) => persist(UserRegister(currentId, email, name)){ev=>
      currentId+=1
      log.info(s"Persisted ${ev}")
    }
  }

  override def receiveRecover: Receive = {
    case event @ UserRegister(id,_,_) => {
      log.info(s"Recovered ${event}")
      currentId=id
    }
  }
}

//Serializer: To define a custom serializer which sits between the persistentActor and the journal, we need to extend
// Serializer in order to be used by akka
object UserRegisterSerializer extends DefaultJsonProtocol{
  implicit val userRegisterImplicit = jsonFormat3(UserRegister)
}

class CustomSerializer extends Serializer{

  import spray.json._
  import UserRegisterSerializer._

  override def identifier: Int = 1984 // Any numer, used to identify the serializer in the akka internals

  override def toBinary(o: AnyRef): Array[Byte] = {
    o  match {
      case event @ UserRegister(id,email,name) => event.toJson.toString().getBytes
      case _ => throw new IllegalArgumentException("Only user registration events supported in this serializer")
    }
  }

  // This method signature takes an array of bytes and it returns the object that you want. The second parameter is in
  // the form of an option Class which is used to instantiate the proper class that you want with reflection.
  // This manifest will be passed with some class if the includeManifest method returns true.
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    // since includeManifest returns false, manifest will be None
    new String(bytes).parseJson.convertTo[UserRegister]
  }

  override def includeManifest: Boolean = false

}

object L4_CustomSerialization extends App{
  val system = ActorSystem("SerializationSystem", ConfigFactory.load().getConfig("SerializationDemo"))
  val actor = system.actorOf(Props[UserRegistrationActor])

  for (i <- 1 to 10) {
    // To check in cassandra => select * from akka.messages
    // The object is still stored in hexadecimal format, which can be easily converted to a string
    actor ! RegisterUser(s"john${i}@test.com", s"John-${i}")
  }
  Thread.sleep(5000)
  system.terminate()
}
