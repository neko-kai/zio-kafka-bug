import java.util.concurrent.atomic.{AtomicLong, LongAdder}

import zio.{UIO, _}

import scala.language.reflectiveCalls

object ZioInterruptLeakOrDeadlockRepo extends zio.App {

  val leakedCounter = new AtomicLong(0L)

  val startedCounter = new LongAdder
  val completedCounter = new LongAdder
  val awakeCounter = new LongAdder
  val interruptedCounter = new LongAdder
  val interruptedWCounter = new LongAdder
  val pendingGauge = new LongAdder
  val timeoutCounter = new LongAdder

  def run(args: List[String]): ZIO[Environment, Nothing, Int] = {
    val leakOrDeadlockTest = for {
      _ <- UIO {
        startedCounter.increment()
        pendingGauge.increment()
      }

      sleepInterruptFiber <- IO.never.fork

      // This blocks / leaks once every 20,000 rounds or so
      _ <- sleepInterruptFiber.interrupt
        .ensuring(UIO {
          awakeCounter.increment()
          pendingGauge.decrement()
        })
        .onInterrupt(UIO(interruptedWCounter.increment()))

      _ <- UIO(completedCounter.increment())
    } yield ()

    val main = for {
      _ <-
        ZIO.runtime[Any].map(_.Platform.executor.metrics.get)
          .flatMap {
            metrics => UIO(new Thread(() => {
              while (true) {
                println(s"started=${startedCounter.longValue()} awake=${awakeCounter.longValue()} completed=${completedCounter.longValue()} pending=${pendingGauge.longValue()} timed-out=${timeoutCounter.longValue()} finishedInterrupts=${interruptedCounter.longValue()} interruptedWaiters=${interruptedWCounter.longValue()} queued=${metrics.size}")
                Thread.sleep(1000L)
              }
            }).start())
          }

      _ <- leakOrDeadlockTest.forever
    } yield 0

    main
  }
}
