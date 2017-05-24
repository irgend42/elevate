package idealised.DSL.untyped.FunctionalPrimitives

import idealised.Core._
import idealised.DSL.untyped.{VisitAndRebuild, _}

import scala.language.postfixOps
import scala.language.reflectiveCalls

final case class Gather(idxF: Expr[ExpType -> ExpType],
                        array: DataExpr) extends PrimitiveExpr {

  override def inferTypes(subs: ExpressionToPhrase.SubstitutionMap): Primitive[ExpType] = {
    import ExpressionToPhrase._

    val array_ = ExpressionToPhrase(array, subs)
    array_.t match {
      case ExpType(ArrayType(n_, dt_)) =>
        val idxF_ = ExpressionToPhrase(idxF, subs)
        idxF_.t match {
          case FunctionType(ExpType(IndexType(m: NatIdentifier)), _) =>
            idealised.FunctionalPrimitives.Gather(n_, dt_, idxF_ `[` n_ `/` m `]`, array_)
          case FunctionType(ExpType(IndexType(m1: Nat)), ExpType(IndexType(m2: Nat)))
            if n_ == m1 && n_ == m2 =>
            idealised.FunctionalPrimitives.Gather(n_, dt_, idxF_, array_)
          case x => error(x.toString, "exp[idx(n)] -> exp[idx(n)]")
        }
      case x => error(x.toString, "exp[n.dt]")
    }
  }

  override def visitAndRebuild(f: VisitAndRebuild.Visitor): DataExpr = {
    Gather(VisitAndRebuild(idxF, f), VisitAndRebuild(array, f))
  }
}