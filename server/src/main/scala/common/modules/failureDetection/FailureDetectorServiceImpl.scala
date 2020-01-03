package common.modules.failureDetection

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.risksense.ipaddr.IpAddress
import common.modules.failureDetection.FailureDetectorConstants.{SUSPICION_DEADLINE, createGrpcSettings}
import common.modules.membership._
import org.slf4j.LoggerFactory
import schema.ImplicitDataConversions._
import service.RequestServiceImpl

import scala.concurrent.{ExecutionContext, Future}


object FailureDetectorServiceImpl


class FailureDetectorServiceImpl(implicit actorSystem: ActorSystem) extends FailureDetectorService {

  implicit private val materializer: ActorMaterializer = ActorMaterializer()
  implicit private val executionContext: ExecutionContext = actorSystem.dispatcher

  final private val log = LoggerFactory.getLogger(RequestServiceImpl.getClass)


  /**
   * RPC for initial failure check, and will be declared SUSPECT if
   * confirmation cannot be returned
   */
  override def directCheck(in: DirectMessage): Future[Confirmation] = {
    log.info("Health check request has been received, attempting to send confirmation")
    Future.successful(Confirmation())
  }

  /**
   * RPC for followup checks, and will be declared DEAD if none of the
   * followup checks are able to be confirmed
   */
  override def followupCheck(in: FollowupMessage): Future[Confirmation] = {

    val ipAddress: IpAddress = in.ipAddress

    log.info(s"Followup check request has been received on suspect ${ipAddress}, attempting to request confirmation")
    FailureDetectorServiceClient(createGrpcSettings(ipAddress, SUSPICION_DEADLINE))
      .directCheck(DirectMessage())
  }
}