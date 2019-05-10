package fr.gospeak.libs.scalautils

import cats.effect.IO
import fr.gospeak.libs.scalautils.Extensions._

import scala.collection.mutable
import scala.reflect.ClassTag

trait MessageBus[A] {
  def subscribe[B <: A : ClassTag](f: B => IO[Unit])

  // not used
  // like subscribe but with a partial function
  // def when[B <: A : ClassTag](pf: PartialFunction[B, IO[Unit]]): Unit

  // returns the number of called subscribers
  def publish[B <: A](msg: B): IO[Int]

  // not used
  // like 'publish' with 'msg' param lazy evaluated but will call only T handlers, not 'msg' concrete type handlers
  // def publishLazy[B <: A : ClassTag](msg: => B): IO[Int]
}

class BasicMessageBus[A] extends MessageBus[A] {
  private val eventHandlers = mutable.Map[Class[_], List[_ => IO[Unit]]]()

  override def subscribe[B <: A : ClassTag](f: B => IO[Unit]): Unit = {
    val key = implicitly[ClassTag[B]].runtimeClass
    eventHandlers.put(key, eventHandlers.getOrElse(key, Nil) :+ f)
  }

  /* override def when[B <: A : ClassTag](pf: PartialFunction[B, IO[Unit]]): Unit = {
    val key = implicitly[ClassTag[B]].runtimeClass
    val f: B => IO[Unit] = pf.applyOrElse(_, (_: B) => IO.pure(()))
    eventHandlers.put(key, eventHandlers.getOrElse(key, Nil) :+ f)
  } */

  override def publish[B <: A](msg: B): IO[Int] = {
    val classes = getClasses(msg.getClass)
    eventHandlers
      .collect { case (clazz, handlers) if classes.contains(clazz) => handlers }
      .flatMap(_.map(_.asInstanceOf[B => IO[Unit]](msg)))
      .toSeq.sequence.map(_.length)
  }

  /* override def publishLazy[B <: A : ClassTag](msg: => B): IO[Int] = {
    lazy val buildMsg = msg // so msg is evaluated only if it's used by at least one handler
    val classes = getClasses(implicitly[ClassTag[B]].runtimeClass)
    eventHandlers
      .collect { case (clazz, handlers) if classes.contains(clazz) => handlers }
      .flatMap(_.map(_.asInstanceOf[B => IO[Unit]](buildMsg)))
      .toList.sequence.map(_.length)
  } */

  private def getClasses(clazz: Class[_]): List[Class[_]] = {
    val parents = Option(clazz.getSuperclass).toList ++ clazz.getInterfaces.toList
    clazz :: parents.flatMap(getClasses)
  }
}
