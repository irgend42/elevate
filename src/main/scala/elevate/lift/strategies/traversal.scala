package elevate.lift.strategies

import elevate.core._
import lift.core._
import lift.core.primitives.map
import elevate.lift.rules.algorithmic._
import elevate.core.strategies.traversal._
import elevate.core.strategies.basic._
import elevate.lift.strategies.algorithmic._
import elevate.lift.strategies.normalForm._
import lift.core.types._

object traversal {

  def traverseSingleSubexpression: Strategy[Lift] => Lift => Option[RewriteResult[Lift]] =
    s => {
      case Identifier(_) => None
      case Lambda(x, e) => Some(s(e).mapSuccess(Lambda(x, _)))
      case DepLambda(x, e) => x match {
        case n: NatIdentifier => Some(s(e).mapSuccess(DepLambda[NatKind](n, _)))
        case dt: DataTypeIdentifier => Some(s(e).mapSuccess(DepLambda[DataKind](dt, _)))
      }
      case DepApply(f, x) => x match {
        case n: Nat => Some(s(f).mapSuccess(DepApply[NatKind](_, n) ))
        case dt: DataType => Some(s(f).mapSuccess(DepApply[DataKind](_, dt) ))
      }
      case Literal(_) => None
      case TypedExpr(e, t) => Some(s(e).mapSuccess(TypedExpr(_, t)))
      case ff: primitives.ForeignFunction => None
      case p: Primitive => None
    }

  implicit object LiftTraversable extends elevate.core.strategies.Traversable[Lift] {
    override def all: Strategy[Lift] => Strategy[Lift] = s => {
      case Apply(f, e) => s(f).flatMapSuccess(a => s(e).mapSuccess(b => Apply(a, b)))

      case x => traverseSingleSubexpression(s)(x) match {
        case Some(r) => r
        case None => Success(x)
      }
    }

    // case class all(s: Strategy[Lift]) extends Strategy[Lift] {
    //    def apply(e: Lift): RewriteResult[Lift] = e match {
    //      case Apply(f, e) => s(f).flatMapSuccess(a => s(e).mapSuccess(b => Apply(a, b) ) )
    //
    //      case x => traverseSingleSubexpression(s)(x) match {
    //        case Some(r) => r
    //        case None => Success(x)
    //      }
    //    }
    //  }
  }

  abstract class Traversal[P](s: Strategy[P]) extends Strategy[P]

  case class body(s: Elevate) extends Traversal[Lift](s) {
    def apply(e: Lift): RewriteResult[Lift] = e match {
      case Lambda(x, f) => s(f).mapSuccess(Lambda(x, _) )
      case _ => Failure(s)
    }
  }

  case class function(s: Elevate) extends Traversal[Lift](s) {
    def apply(e: Lift): RewriteResult[Lift] = e match {
      case Apply(f, e) => s(f).mapSuccess(Apply(_, e))
      case _ => Failure(s)
    }
  }

  // todo move to meta package
  case class inBody(s: Meta) extends Traversal[Elevate](s) {
    def apply(e: Elevate): RewriteResult[Elevate] = e match {
      case body(x: Elevate) => s(x).mapSuccess(body)
      case _ => Failure(s)
    }
  }

  def argument: Elevate => Elevate =
    s => {
      case Apply(f, e) => s(e).mapSuccess(Apply(f, _))
      case _ => Failure(s)
    }

  def argumentOf(x: Primitive): Elevate => Elevate = {
    s => {
      case Apply(f, e) if f == x => s(e).mapSuccess(Apply(f, _))
      case _ => Failure(s)
    }
  }

  // applying a strategy to an expression applied to a lift `map`. Example:
  // ((map λe14. (transpose ((map (map e12)) e14))) e13) // input expr
  //  (map λe14. (transpose ((map (map e12)) e14)))      // result of `function`
  //       λe14. (transpose ((map (map e12)) e14))       // result of `argument`
  //             (transpose ((map (map e12)) e14))       // result of 'body' -> here we can apply s
  def fmap: Elevate => Elevate = s => function(argumentOf(map)(body(s)))

  // fmap applied for expressions in rewrite normal form:
  // fuse -> fmap -> fission
  def fmapRNF: Elevate => Elevate =
    s => LCNF `;` mapFusion `;`
      LCNF `;` fmap(s) `;`
      LCNF `;` one(mapFullFission)

  // applying a strategy to an expression nested in one or multiple lift `map`s
  def mapped: Elevate => Elevate =
    s => s <+ (e => fmapRNF(mapped(s))(e))

  // moves along RNF-normalized expression
  // e.g., expr == ***f o ****g o *h
  // move(0)(s) == s(***f o ****g o *h)
  // move(1)(s) == s(****g o *h)
  // move(2)(s) == s(*h)
  def moveTowardsArgument: Int => Elevate => Elevate =
    i => s => applyNTimes(i, argument(_), s)
}
