package idealised.OpenCL.SurfaceLanguage.Primitives

import idealised.DPIA.Phrases._
import idealised.DPIA.Types.{TypeInference, _}
import idealised.DPIA._
import idealised.OpenCL.AddressSpace
import idealised.SurfaceLanguage
import idealised.SurfaceLanguage.DSL.DataExpr
import idealised.SurfaceLanguage.{Expr, PrimitiveExpr}

abstract class To(f: Expr[ExpType -> ExpType],
                  input: DataExpr,
                  addressSpace: AddressSpace,
                  private val makeTo: (Expr[ExpType -> ExpType], DataExpr) => To,
                  private val makeToPhrase: (DataType, DataType,
                   Phrase[ExpType -> ExpType], Phrase[ExpType]) => idealised.OpenCL.FunctionalPrimitives.To)
  extends PrimitiveExpr {

  override def inferTypes(subs: TypeInference.SubstitutionMap): Primitive[ExpType] = {
    import TypeInference._
    val input_ = TypeInference(input, subs)
    input_.t match {
      case ExpType(dt1_) =>
        val f_ = TypeInference.setParamAndInferType(f, exp"[$dt1_]", subs)
        f_.t match {
          case FunctionType(ExpType(t1_), ExpType(dt2_)) =>
            if (dt1_ == t1_) {
              makeToPhrase(dt1_, dt2_, f_, input_)
            } else {
              error(this, dt1_.toString + " and " + t1_.toString, expected = "them to match")
            }
          case x => error(this, x.toString, "exp[dt1] -> exp[dt2]")
        }
      case x => error(this, x.toString, "exp[dt]")
    }
  }

  override def visitAndRebuild(fun: SurfaceLanguage.VisitAndRebuild.Visitor): DataExpr = {
    makeTo(SurfaceLanguage.VisitAndRebuild(f, fun), SurfaceLanguage.VisitAndRebuild(input, fun))
  }

}
