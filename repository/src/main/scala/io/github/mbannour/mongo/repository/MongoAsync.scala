package io.github.mbannour.mongo.repository

import org.mongodb.scala.*

import scala.concurrent.{ExecutionContext, Future}

/** Bridges the MongoDB Scala driver's reactive results into an arbitrary effect `F`.
 *
 * The repository layer is written entirely against this small type class, so the same
 * [[MongoRepository]] works with `scala.concurrent.Future` out of the box and with ZIO,
 * cats-effect `IO`, Monix, etc. by supplying the corresponding instance in a thin integration
 * module — without changing any repository code.
 *
 * Only five primitives are needed; everything in [[MongoRepository]] is expressed in terms of them.
 */
trait MongoAsync[F[_]]:

  /** Run an observable expected to emit exactly one element (insert/update/delete results, counts). */
  def single[A](obs: => SingleObservable[A]): F[A]

  /** Run an observable and capture its first element, if any (find-one / find-and-modify). */
  def optional[A](obs: => Observable[A]): F[Option[A]]

  /** Run an observable and collect every emitted element (find-many). */
  def many[A](obs: => Observable[A]): F[Seq[A]]

  /** Transform the result of an effect. */
  def map[A, B](fa: F[A])(f: A => B): F[B]

  /** Lift a pure value into the effect (used for no-op fast paths such as empty batch inserts). */
  def pure[A](a: => A): F[A]

object MongoAsync:

  def apply[F[_]](using F: MongoAsync[F]): MongoAsync[F] = F

  /** Default instance backed by `scala.concurrent.Future`.
   *
   * A ZIO / cats-effect / fs2 instance is a few lines each and belongs in its own integration module.
   */
  given future(using ec: ExecutionContext): MongoAsync[Future] with
    def single[A](obs: => SingleObservable[A]): Future[A]     = obs.toFuture()
    def optional[A](obs: => Observable[A]): Future[Option[A]] = obs.headOption()
    def many[A](obs: => Observable[A]): Future[Seq[A]]        = obs.toFuture()
    def map[A, B](fa: Future[A])(f: A => B): Future[B]        = fa.map(f)
    def pure[A](a: => A): Future[A]                           = Future.successful(a)
