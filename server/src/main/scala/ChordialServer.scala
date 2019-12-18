import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import persistence.io.PersistenceActor
import persistence.threading.ThreadPartitionActor
import service.RequestServiceInitializer


private object ChordialServer extends App {

  val config = ConfigFactory.load()

  val log = LoggerFactory.getLogger(ChordialServer.getClass)
  log.info("Server config loaded")


  log.info("Initializing actor system")
  implicit val actorSystem: ActorSystem = ActorSystem("Chordial", config)


  // Persistence layer top-level actors
  log.info("Initializing top-level persistence actors")
  val threadPartitionActor = ThreadPartitionActor()
  val persistenceActor = PersistenceActor(threadPartitionActor)
  log.info("Persistence layer top-level actors created")

  log.info("Initializing gRPC service")
  RequestServiceInitializer(persistenceActor).run()
  log.info("gRPC service initialized")
}