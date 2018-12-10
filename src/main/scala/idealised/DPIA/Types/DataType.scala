package idealised.DPIA.Types

import idealised.DPIA.{Nat, Types}
import idealised.SurfaceLanguage
import idealised.SurfaceLanguage.Types.TupleType
import idealised.utils.SizeInByte

import scala.language.implicitConversions

sealed trait DataType

sealed trait ComposedType extends DataType

sealed trait BasicType extends DataType

sealed trait ScalarType extends BasicType

object bool extends ScalarType { override def toString: String = "bool" }

object int extends ScalarType { override def toString: String = "int" }

object float extends ScalarType { override def toString: String = "float" }

final case class IndexType(size: Nat) extends BasicType {
  override def toString: String = s"idx($size)"
}

final case class ArrayType(size: Nat, elemType: DataType) extends ComposedType {
  override def toString: String = s"$size.$elemType"
}

final case class DepArrayType(size:Nat, elemType:NatDependentFunctionType[ExpType]) extends ComposedType {
  override def toString: String = s"$size.$elemType"
}

final case class RecordType(fst: DataType, snd: DataType) extends ComposedType {
  override def toString: String = s"($fst x $snd)"
}

sealed case class VectorType(size: Nat, elemType: ScalarType) extends BasicType {
  override def toString: String = s"$elemType$size"
}

object int2 extends VectorType(2, int)
object int3 extends VectorType(3, int)
object int4 extends VectorType(4, int)
object int8 extends VectorType(8, int)
object int16 extends VectorType(16, int)

object float2 extends VectorType(2, float)
object float3 extends VectorType(3, float)
object float4 extends VectorType(4, float)
object float8 extends VectorType(8, float)
object float16 extends VectorType(16, float)

final case class DataTypeIdentifier(name: String) extends DataType {
  override def toString: String = name
}

object ScalarType {
  implicit def apply(st: SurfaceLanguage.Types.ScalarType): ScalarType = {
    st match {
      case SurfaceLanguage.Types.bool => bool
      case SurfaceLanguage.Types.int => int
      case SurfaceLanguage.Types.float => float
    }
  }
}

object DataType {
  implicit def apply(dt: SurfaceLanguage.Types.DataType): DataType = {
    dt match {
      case bt: SurfaceLanguage.Types.BasicType => bt match {
        case st: SurfaceLanguage.Types.ScalarType => ScalarType(st)
        case vt: SurfaceLanguage.Types.VectorType => VectorType(vt.size, ScalarType(vt.elemType))
        case it: SurfaceLanguage.Types.IndexType => IndexType(it.size)
      }
      case ct: SurfaceLanguage.Types.ComposedType => ct match {
        case at: SurfaceLanguage.Types.ArrayType => ArrayType(at.size, DataType(at.elemType))
        case dat:SurfaceLanguage.Types.DepArrayType =>
          DepArrayType(dat.size, NatDependentFunctionType(dat.elemType.x, ExpType(DataType(dat.elemType.t))))
        case tt: SurfaceLanguage.Types.TupleType => {
          assert(tt.elemTypes.size == 2)
          RecordType(DataType(tt.elemTypes(0)), DataType(tt.elemTypes(1)))
        }
      }
      case i: SurfaceLanguage.Types.DataTypeIdentifier => DataTypeIdentifier(i.name)
    }
  }

  // TODO: should not be in this file
  def toType(dt: DataType): ir.Type = {
    dt match {
      case b: BasicType => b match {
        case Types.bool => opencl.ir.Int
        case Types.int => opencl.ir.Int
        case Types.float => opencl.ir.Float
        case _: IndexType => opencl.ir.Int
        case v: VectorType => toVectorType(v)
      }
      case a: ArrayType => ir.ArrayType(DataType.toType(a.elemType), a.size)
      case a: DepArrayType => ir.ArrayType(DataType.toType(a.elemType.t.dataType), a.size)
      case r: RecordType => ir.TupleType(DataType.toType(r.fst), DataType.toType(r.snd))
      case _: DataTypeIdentifier => throw new Exception("This should not happen")
    }
  }

  // TODO: should not be in this file
  def toVectorType(v: VectorType): ir.VectorType = {
    ir.VectorType(DataType.toType(v.elemType) match {
      case s: ir.ScalarType => s
      case _ => throw new Exception("This should not happen")
    }, v.size)
  }

  // TODO: should not be in this file
  def scalarType(dt: DataType): ir.ScalarType = {
    dt match {
      case b: BasicType => b match {
        case Types.bool => opencl.ir.Int
        case Types.int => opencl.ir.Int
        case Types.float => opencl.ir.Float
        case _: IndexType => opencl.ir.Int
        case v: VectorType => scalarType(v.elemType)
      }
      case a: ArrayType => scalarType(a.elemType)
      case a: DepArrayType => scalarType(a.elemType.t.dataType)
      case _: RecordType => ???
      case _: DataTypeIdentifier => throw new Exception("This should not happen")
    }
  }

  def getLengths(dt: DataType, tupleAccesss: List[Nat], list: List[Nat]): List[Nat] = {
    dt match {
      case _: BasicType => 1 :: list
      case r: RecordType =>
        val t = tupleAccesss.head
        val elemT = if (t == (1: Nat)) { r.fst } else if (t == (2: Nat)) { r.snd } else { throw new Exception("This should not happen") }
        getLengths(elemT, tupleAccesss.tail, list)
      case a: ArrayType => getLengths(a.elemType, tupleAccesss, a.size :: list)
      case dep: DepArrayType => getLengths(dep.elemType.t.dataType, tupleAccesss, dep.size::list) //TODO: Probably wrong
      case _: DataTypeIdentifier => throw new Exception("This should not happen")
    }
  }

  def getBaseDataType(dt: DataType): DataType = {
    dt match {
      case _: BasicType => dt
      case _: RecordType => dt
      case _: DataTypeIdentifier => dt
      case ArrayType(_, dt) => getBaseDataType(dt)
      case DepArrayType(_, elemT) => elemT.t.dataType
    }
  }

  implicit class RecordTypeConstructor(dt1: DataType) {
    def x(dt2: DataType) = RecordType(dt1, dt2)
  }

  implicit class ArrayTypeConstructor(s: Nat) {
    def `.`(dt: DataType) = ArrayType(s, dt)
  }

  // TODO: should not be in this file
  def sizeInByte(dt: DataType): SizeInByte = sizeInByte(toType(dt))

  // TODO: should not be in this file
  def sizeInByte(t: ir.Type): SizeInByte = {
    t match {
      case s: ir.ScalarType => SizeInByte(s.size)
      case v: ir.VectorType => sizeInByte(v.scalarT) * v.len
      case t: ir.TupleType => t.elemsT.map(sizeInByte).reduce(_+_)
      case a: ir.ArrayType with ir.Size => sizeInByte(a.elemT) * a.size
      case _: ir.NoType.type | _: ir.UndefType.type | _: ir.ArrayType /* without Size */ =>
        throw new Exception("This should not happen")
    }
  }
}