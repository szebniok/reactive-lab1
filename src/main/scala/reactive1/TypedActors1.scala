package reactive1

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import scala.concurrent.duration._
import scala.concurrent.Await
//////////////////////////////////////////
// Introduction to Scala (Akka [Typed]) Actors  //
//////////////////////////////////////////

/**
 * Actor:
 * - an object with identity
 * - with a behavior
 * - interacting only via asynchronous messages
 *
 * Consequently: actors are fully encapsulated / isolated from each other
 * - the only way to exchange state is via messages (no global synchronization)
 * - all actors run fully concurrently
 *
 * Messages:
 * - are received sequentially and enqueued
 * - processing one message is atomic
 */
/**
 * object Behaviors {
 *   def setup[T](factory: ActorContext[T] => Behavior[T]): Behavior[T]
 *   def receive[T](onMessage: (ActorContext[T], T) => Behavior[T]): Receive[T]
 *   ...
 * }
 *
 * abstract class AbstractBehavior[T](protected val context: ActorContext[T]) extends ExtensibleBehavior[T] {
 *   def onMessage(msg: T): Behavior[T]
 *   ...
 * }
 *
 * API documentation: https://akka.io/docs/
 */
/**
 * Logging options: read article
 * https://doc.akka.io/docs/akka/current/typed/logging.html
 *
 * a) Behaviors.setup[String] { context =>
 *   context.setLoggerName("com.myservice.BackendManager")
 *   context.log.info("Starting up")
 *   ...
 * }
 *
 * b) ActorContext.log
 * trait ActorContext[T] extends TypedActorContext[T] with ClassicActorContextProvider {
 *   def log: Logger
 *   ...
 * }
 *
 * c) Behaviors.logMessages
 *  Behaviors.logMessages(MyBehavior())
 */
// 1. Functional Way
object TypedCounter {
  trait Command
  final case object Increment                                       extends Command
  final case class Get(replyTo: ActorRef[TypedCounterMain.Message]) extends Command

  private def apply(count: Int): Behavior[Command] =
    Behaviors.receiveMessage {
      case Increment =>
        println(Thread.currentThread.getName + ".")
        TypedCounter.apply(count + 1)
      case Get(replyTo: ActorRef[TypedCounterMain.Message]) =>
        replyTo ! TypedCounterMain.Count(count)
        Behaviors.same
    }

  def apply(): Behavior[Command] = apply(0)
}

// 2. OOP way
object TypedCounterOOP {
  trait Command
  final case object Increment                                       extends Command
  final case class Get(replyTo: ActorRef[TypedCounterMain.Message]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup(context => new TypedCounterOOP(context))
}

class TypedCounterOOP(
  context: ActorContext[TypedCounterOOP.Command]
) extends AbstractBehavior[TypedCounterOOP.Command](context) {
  import TypedCounterOOP._
  var count = 0

  override def onMessage(msg: TypedCounterOOP.Command): Behavior[TypedCounterOOP.Command] =
    msg match {
      case Increment =>
        context.log.info(Thread.currentThread.getName + ".")
        count += 1
        this
      case Get(replyTo) =>
        replyTo ! TypedCounterMain.Count(count)
        this
    }
}

/**
 * Sending messages: "tell" method
 *
 *  trait ActorRef[-T] {
 *    def tell(msg: T): Unit
 *    ...
 *  }
 */
object TypedCounterMain {
  sealed trait Message
  case object Init             extends Message
  case class Count(count: Int) extends Message

  def initCounterMain(): Behavior[Message] =
    Behaviors.receive((context, msg) =>
      msg match {
        case Init =>
          val counter = context.spawn(TypedCounter.apply() /* or CounterOOP() */, "counter")
          counter ! TypedCounter.Increment
          counter ! TypedCounter.Increment
          counter ! TypedCounter.Increment
          counter ! TypedCounter.Get(context.self)
          Behaviors.same
        case Count(count) =>
          println(s"count received: $count")
          println(Thread.currentThread.getName + ".")
          context.system.terminate
          Behaviors.same
      }
    )
}

object TypedApplicationMain extends App {
  val system: ActorSystem[TypedCounterMain.Message] = ActorSystem(TypedCounterMain.initCounterMain(), "Reactive1")

  system ! TypedCounterMain.Init

  Await.result(system.whenTerminated, Duration.Inf)
}
