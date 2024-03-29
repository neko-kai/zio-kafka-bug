import java.time.OffsetDateTime
import java.util.UUID

import fs2.Stream
import fs2.kafka.{ProducerRecord, ProducerRecords, ProducerSettings, produce}
import zio._
import zio.interop.catz._

object StreamZio extends App {
  private val producerSettings = ProducerSettings[Task, String, String]
    .withBootstrapServers("http://localhost:9092")
    .withParallelism(1000)

  override def run(args: List[String]): ZIO[Any, Nothing, Int] = ZIO.runtime.flatMap { implicit rt: Runtime[Any] =>
    val string = UUID.randomUUID().toString
    val zioRecord = ProducerRecords.one(ProducerRecord("notifications-zio-" + string, "", "some zio data"))

    Stream(zioRecord)
      .repeat
      .through(produce(producerSettings))
      .chunkN(5000)
      .evalMap[Task, Unit](_ => Task.descriptor.flatMap { f =>
        Task { println(s"${OffsetDateTime.now()} FiberId(${f.id}) -> Processed batch of 5.000 items") }
      }).compile.drain.as(0).orDie
  }
}
