package idealised.MidLevelCombinators

import idealised._
import idealised.Core._
import idealised.Core.OperationalSemantics._
import idealised.Compiling.SubstituteImplementations
import idealised.DSL.typed._
import idealised.LowLevelCombinators.{TruncAcc, TruncExp}
import idealised.OpenCL.Core.ToOpenCL
import apart.arithmetic.{?, Cst, RangeAdd}

import scala.xml.Elem

final case class IterateIAcc(n: Nat,
                             m: Nat,
                             k: Nat,
                             dt: DataType,
                             out: Phrase[AccType],
                             f: Phrase[`(nat)->`[AccType -> (ExpType -> CommandType)]],
                             in: Phrase[ExpType])
  extends MidLevelCombinator {

  override def typeCheck(): Unit = {
    import TypeChecker._
    f match {
      case NatDependentLambdaPhrase(l, _) =>
        (n: Nat) -> (m: Nat) -> (k: Nat) -> (dt: DataType) ->
          (out `:` acc"[$m.$dt]") ->
          (f `:` t"($l : nat) -> acc[${l /^ n}.$dt] -> exp[$l.$dt] -> comm") ->
          (in `:` exp"[${n.pow(k) * m}.$dt]") ->
          comm

      case _ => throw new Exception("This should not happen")
    }
  }

  override def eval(s: Store): Store = ???

  override def visitAndRebuild(fun: VisitAndRebuild.Visitor): Phrase[CommandType] = {
    IterateIAcc(fun(n), fun(m), fun(k), fun(dt),
      VisitAndRebuild(out, fun),
      VisitAndRebuild(f, fun),
      VisitAndRebuild(in, fun))
  }

  override def substituteImpl(env: SubstituteImplementations.Environment): Phrase[CommandType] = {
    // infer the address space from the output
    val identifier = ToOpenCL.acc(out, ToOpenCL.Environment(?, ?))
    val addressSpace = env.addressSpace(identifier.name)

    val `n^k*m` = n.pow(k) * m

    `new`(dt"[${`n^k*m`}.$dt]", addressSpace, buf1 =>
      `new`(dt"[${`n^k*m`}.$dt]", addressSpace, buf2 =>
        SubstituteImplementations(MapI(`n^k*m`, dt, dt, buf1.wr,
          λ(acc"[$dt]")(a => λ(exp"[$dt]")(e => a `:=` e)), in), env) `;`
          dblBufFor(`n^k*m`, m, k, dt, addressSpace, buf1, buf2,
            _Λ_(l => λ(acc"[${`n^k*m`}.$dt]")(a => λ(exp"[${`n^k*m`}.$dt]")(e =>
              SubstituteImplementations(
                f (n.pow(k - l) * m)
                  (TruncAcc(`n^k*m`, n.pow(k - l - 1) * m, dt, a))
                  (TruncExp(`n^k*m`, n.pow(k - l    ) * m, dt, e)), env)))),
            λ(exp"[$m.$dt]")(x =>
              SubstituteImplementations(MapI(m, dt, dt, out,
                λ(acc"[$dt]")(a => λ(exp"[$dt]")(e => a `:=` e)), x), env)))))
  }

  override def prettyPrint: String = s"(iterateIAcc ${PrettyPrinter(out)} ${PrettyPrinter(f)} ${PrettyPrinter(in)})"

  override def xmlPrinter: Elem = {
    val l = f match {
      case NatDependentLambdaPhrase(l_, _) => l_
      case _ => throw new Exception("This should not happen")
    }
    <iterateIAcc n={ToString(n)} m={ToString(m)} k={ToString(k)} dt={ToString(dt)}>
      <output type={ToString(AccType(ArrayType(m, dt)))}>
        {Core.xmlPrinter(out)}
      </output>
      <f type={ToString(l -> (AccType(ArrayType(l /^ n, dt)) -> (ExpType(ArrayType(l, dt)) -> CommandType())))}>
        {Core.xmlPrinter(f)}
      </f>
      <input type={ToString(ExpType(ArrayType(n.pow(k) * m, dt)))}>
        {Core.xmlPrinter(in)}
      </input>
    </iterateIAcc>
  }
}
