package replication.state

import administration.Membership
import com.risksense.ipaddr.IpAddress
import common.persistence.{JavaSerializer, PersistentVal}
import replication.Raft
import replication.state.RaftState._

import scala.collection.View
import scala.collection.immutable.{HashMap, HashSet}


abstract class RaftCluster(self: Membership) {

  // Map the nodeID -> numerical IP address, since the IP address object does not implement
  // the Java serializable interface
  private val raftMembership: PersistentVal[HashMap[String, Long]] =
    new PersistentVal[HashMap[String, Long]](Raft.RAFT_DIR/("cluster" + RAFT_STATE_EXTENSION))
        with JavaSerializer[HashMap[String, Long]]

  private var attemptedQuorum: Set[String] = new HashSet[String]()

  raftMembership.write(HashMap[String, Long](
    self.nodeID -> self.ipAddress.numerical
  ))


  def get: Iterable[Membership] = raftMembership.read().get.map {
    case (nodeID, ipAddress) => Membership(nodeID, IpAddress(ipAddress))
  }

  def member(nodeID: String): Membership =
    Membership(nodeID, IpAddress(raftMembership.read().get.apply(nodeID)))

  def foreach(f: Membership => Unit): Unit =
    get.foreach(f)

  def cluster(): View[Membership] =
    get.view

  def iterator(): Iterator[Membership] =
    get.iterator

  def clusterSize(): Int =
    raftMembership.read().get.size


  def registerReply(nodeID: String): Unit = if (raftMembership.read().get.contains(nodeID)) {
    attemptedQuorum += nodeID
  }

  def hasQuorum: Boolean =
    attemptedQuorum.size > (raftMembership.read().get.size / 2)

  def resetQuorum(): Unit =
    attemptedQuorum = HashSet[String](self.nodeID)

  def numReplies(): Int =
    attemptedQuorum.size
}
