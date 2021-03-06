package replication.eventlog

import java.io.{FileOutputStream, RandomAccessFile}

import better.files.File
import common.persistence.JavaSerializer
import org.slf4j.{Logger, LoggerFactory}
import replication.eventlog.ReplicatedLog.Offset

import scala.collection.mutable
import scala.util.{Failure, Success}


class SimpleReplicatedLog(
    private val indexFile: File,
    private val dataFile: File
  )
  extends ReplicatedLog {

  import SimpleReplicatedLog._

  dataFile.createFileIfNotExists(createParents = true)

  private val dataReader: RandomAccessFile = dataFile.newRandomAccess(File.RandomAccessMode.read)
  private val dataAppender: FileOutputStream = dataFile.newFileOutputStream(append = true)

  private val metadata: LogMetadata = {
    if (indexFile.exists) LogMetadata.deserialize(indexFile.loadBytes) match {
      case Success(metadata)  => metadata
      case Failure(exception) => throw exception
    }
    else {
      indexFile.createFileIfNotExists(createParents = true)
      val newMetadata = LogMetadata(INIT_LOG_INDEX)
      saveMetadata(newMetadata)
      newMetadata
    }
  }

  private val log: Logger = LoggerFactory.getLogger(SimpleReplicatedLog.getClass)


  override def apply(index: Int): Array[Byte] = {
    val entry = new Array[Byte](lengthOf(index))
    log.debug(s"Retrieving log entry #$index at offset ${offsetOf(index)} and byte length ${lengthOf(index)} from WAL")

    dataReader.seek(offsetOf(index))
    dataReader.readFully(entry)

    log.debug(s"Retrieved log entry: ${entry.map("%02X" format _).mkString}")
    entry
  }

  // Note: important that metadata is updated AFTER the data itself, to prevent invalid state
  override def append(term: Long, entry: Array[Byte]): Unit = {
    val logIndex = LogIndex(dataFile.size.toInt, entry.length, term)
    log.debug(s"Appending log entry #${metadata.size} at offset ${logIndex.offset} and byte length ${logIndex.length} to WAL")

    dataAppender.write(entry)
    dataAppender.flush()
    metadata.append(term, logIndex)
    saveMetadata(metadata)

    log.debug(s"Appended log entry: ${entry.map("%02X" format _).mkString}")
  }

  override def slice(from: Int, until: Int): Array[Byte] = {
    if (from >= until) {
      throw new IllegalArgumentException("Range is invalid: left bound must strictly be smaller than right bound")
    }

    val sliceLength =
      metadata.offsetIndex(until - 1).length +
      metadata.offsetIndex(until - 1).offset -
      metadata.offsetIndex(from).offset

    val entry = new Array[Byte](sliceLength)
    dataReader.read(entry, metadata.offsetIndex(from).offset, sliceLength)
    entry
  }

  override def size: Offset =
    metadata.offsetIndex.size

  override def rollback(newSize: Offset): Unit = {
    if (newSize < 0 || newSize > size) {
      throw new IllegalArgumentException(s"Illegal new size: $newSize")
    }

    metadata.offsetIndex.trimEnd(size - newSize)
    metadata.lastIncludedTerm = termOf(lastLogIndex())

    saveMetadata(metadata)
  }


  override def lastLogTerm(): Long =
    metadata.lastIncludedTerm

  override def lastLogIndex(): Int =
    size - 1

  override def offsetOf(index: Int): Offset =
    metadata.offsetIndex(index).offset

  override def lengthOf(index: Int): Offset =
    metadata.offsetIndex(index).length

  override def termOf(index: Int): Long =
    metadata.offsetIndex(index).term


  private def saveMetadata(metadata: LogMetadata): Unit = {
    LogMetadata.serialize(metadata) match {
      case Success(bytes)     => indexFile.writeByteArray(bytes)
      case Failure(exception) => throw exception
    }
  }

}

private object SimpleReplicatedLog {

  private object LogMetadata extends JavaSerializer[LogMetadata] {

    def apply(elems: LogIndex*): LogMetadata =
      new LogMetadata(lastIncludedTerm = 0, mutable.ListBuffer[LogIndex](elems: _*))
  }

  @SerialVersionUID(100L)
  private class LogMetadata(
    var lastIncludedTerm: Long,
    val offsetIndex: mutable.Buffer[LogIndex]
  ) extends Serializable {

    def append(term: Long, logIndex: LogIndex): Unit = {
      if (term > lastIncludedTerm) lastIncludedTerm = term
      offsetIndex.addOne(logIndex)
    }

    def size: Int = offsetIndex.size
  }


  private object LogIndex extends JavaSerializer[LogIndex]

  private final case class LogIndex(
    offset: Offset,
    length: Int,
    term: Long,
  )

  private val INIT_LOG_INDEX: LogIndex = LogIndex(0, 0, 0)
}
