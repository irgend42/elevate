package idealised.OpenCL.FunctionalPrimitives

import idealised.DPIA.Compilation.{TranslationContext, TranslationToImperative}
import idealised.DPIA.DSL.{`new` => _, _}
import idealised.DPIA.Phrases._
import idealised.DPIA.Semantics.OperationalSemantics
import idealised.DPIA.Semantics.OperationalSemantics.{Data, Store}
import idealised.DPIA.Types._
import idealised.DPIA.{Phrases, _}
import idealised.OpenCL.DSL.newWithAddrSpace

import scala.xml.Elem

abstract class To(addrSpace: AddrSpace,
                  dt: DataType,
                  input: Phrase[ExpType],
                  private val makeTo: (DataType, Phrase[ExpType]) => To)
  extends ExpPrimitive {

  override val t: ExpType =
    (addrSpace : AddrSpace) -> (dt: DataType) ->
      (input :: exp"[$dt, $Write]") -> exp"[$dt, $Read]"

  override def eval(s: Store): Data = OperationalSemantics.eval(s, input)

  override def visitAndRebuild(fun: VisitAndRebuild.Visitor): Phrase[ExpType] = {
    makeTo(fun(dt), VisitAndRebuild(input, fun))
  }

  override def prettyPrint: String =
    s"(to$addrSpace ${PrettyPhrasePrinter(input)})"

  override def xmlPrinter: Elem =
    <to addrSpace={ToString(addrSpace)} dt={ToString(dt)}>
      <input type={ToString(ExpType(dt, Write))}>
        {Phrases.xmlPrinter(input)}
      </input>
    </to>.copy(label = {
      val name = this.getClass.getSimpleName
      Character.toLowerCase(name.charAt(0)) + name.substring(1)
    })

  override def acceptorTranslation(A: Phrase[AccType])
                                  (implicit context: TranslationContext): Phrase[CommandType] = {
    ???
  }

  override def mapAcceptorTranslation(f: Phrase[ExpType -> ExpType], A: Phrase[AccType])
                                     (implicit context: TranslationContext): Phrase[CommandType] = {
    ???
  }

  override def continuationTranslation(C: Phrase[->[ExpType, CommandType]])
                                      (implicit context: TranslationContext): Phrase[CommandType] = {
    import TranslationToImperative._

    newWithAddrSpace(addrSpace, dt, tmp => acc(input)(tmp.wr) `;` C(tmp.rd) )
  }
}
