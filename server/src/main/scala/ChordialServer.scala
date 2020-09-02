import administration.{Administration, Membership}
import administration.addresser.KubernetesAddresser
import administration.failureDetection.FailureDetectorGRPCService
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, Behaviors}
import ch.qos.logback.classic.Level
import com.typesafe.config.ConfigFactory
import common.ServerConstants._
import common.administration.types.NodeState
import org.slf4j.LoggerFactory
import persistence.PersistenceActor
import persistence.execution.PartitionedTaskExecutor
import replication.{RaftGRPCService, ReplicationComponent}
import schema.LoggingConfiguration


private object ChordialServer extends App {

  val config = ConfigFactory.load()

  LoggingConfiguration.setPackageLevel(Level.INFO,
    "io.grpc.netty",
    "akka.http.impl.engine.http2",
    "akka.io",
    "akka.actor"
  )

  val log = LoggerFactory.getLogger(ChordialServer.getClass)
  log.info("Server config loaded")

  log.info("Initializing actor system")
  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.setup { context =>
    new AbstractBehavior[Nothing](context) { override def onMessage(msg: Nothing): Behavior[Nothing] = {

      /**
       * Membership module components
       *   TODO: eventually allow different address retriever methods
       */
      log.info("Initializing membership module components")
      val addressRetriever = KubernetesAddresser
      val membershipActor = context.spawn(Administration(addressRetriever, REQUIRED_TRIGGERS), "administration")
      FailureDetectorGRPCService()

      log.info("Membership module components initialized")

      context.spawn()

      this
    }}
  }, "ChordialServer", config)





  /**
   * Persistence layer components
   */
  log.info("Initializing top-level persistence layer components")

  val threadPartitionActor = PartitionedTaskExecutor()
  val persistenceActor = PersistenceActor(threadPartitionActor, membershipActor)

  log.info("Persistence layer components created")


  /**
   * Replication layer components
   */
  log.info("Initializing raft and replication layer components")

  val replicationActor = ReplicationActor(addressRetriever, persistenceActor)

  log.info("Replication layer components created")


  /**
   * Service layer components
   */
  log.info("Initializing external facing gRPC service")

  val requestServiceActor = ServiceActor(persistenceActor, membershipActor)
  RequestServiceImpl(requestServiceActor, membershipActor)

  log.info("Service layer components created")


  scala.sys.addShutdownHook(membershipActor ! DeclareEvent(
    NodeState.DEAD,
    Membership(Administration.nodeID, addressRetriever.selfIP)
  ))
}
