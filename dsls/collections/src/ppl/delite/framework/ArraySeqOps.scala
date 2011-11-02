package ppl.delite.framework.collections



import ppl.delite.framework.DeliteApplication
import scala.virtualization.lms.common._
import ppl.delite.framework.datastruct.scala.DeliteCollection
import java.io.PrintWriter
import ppl.delite.framework.datastruct.scala._



trait ArraySeqOps extends SeqOps {
  
  /* ctors */
  // TODO - array buffer
  object ArraySeq {
    def apply[T: Manifest](length: Rep[Int]) = arrayseq_obj_new(length)
    def range(length: Rep[Int]) = arrayseq_obj_new_range(length)
    def fromArray[T: Manifest](arr: Rep[Array[T]]) = arrayseq_obj_new_fromarray(arr)
    def fromArrayBuffer[T: Manifest](ab: Rep[collection.mutable.ArrayBuffer[T]]) = arrayseq_obj_new_fromarraybuffer(ab)
  }
  
  /* lifting */
  implicit def arrayseqrep2traversableops[T: Manifest](t: Rep[ArraySeq[T]]): TraversableClsOps[T, ArraySeq[T]] = new TraversableClsOps[T, ArraySeq[T]](t)
  implicit def arrayseqrep2arrayseqops[T: Manifest](arrseq: Rep[ArraySeq[T]]) = new ArraySeqOpsCls(arrseq)
  
  class ArraySeqOpsCls[T: Manifest](arrseq: Rep[ArraySeq[T]]) {
  }
  
  /* object defs */
  def arrayseq_obj_new[T: Manifest](length: Rep[Int]): Rep[ArraySeq[T]]
  def arrayseq_obj_new_range(length: Rep[Int]): Rep[ArraySeq[Int]]
  def arrayseq_obj_new_fromarray[T: Manifest](arr: Rep[Array[T]]): Rep[ArraySeq[T]]
  def arrayseq_obj_new_fromarraybuffer[T: Manifest](arr: Rep[collection.mutable.ArrayBuffer[T]]): Rep[ArraySeq[T]]
  
  /* class interface defs */
  
  /* implicit rules */
  implicit def arraySeqCanBuild[T: Manifest, S: Manifest]: CanBuild[ArraySeq[T], S, ArraySeq[S]]
  
}


trait ArraySeqOpsExp extends SeqOpsExp with ArraySeqOps {
self: HashMapOpsExp with ArraySeqEmitting with HashMultiMapEmitting =>
  
  // TODO: remove later, but - the implicit conversion below _is_ resolved, unlike the one in the supertrait - why?
  //implicit def arrayseqrep2traversableops2[T: Manifest](t: Rep[ArraySeq[T]]): TraversableClsOps[T, ArraySeq[T]] = new TraversableClsOps[T, ArraySeq[T]](t)
  
  /* node defs */
  case class ArraySeqNew[T](len: Exp[Int])(val mV: Manifest[ArraySeqImpl[T]]) extends Def[ArraySeq[T]]
  case class ArraySeqNewRange(len: Exp[Int])(val mV: Manifest[ArraySeqImpl[Int]]) extends Def[ArraySeq[Int]]
  case class ArraySeqNewFromArray[T](arr: Exp[Array[T]])(val mV: Manifest[ArraySeqImpl[T]]) extends Def[ArraySeq[T]]
  case class ArraySeqNewFromArrayBuffer[T](ab: Exp[collection.mutable.ArrayBuffer[T]])(val mV: Manifest[ArraySeqImpl[T]]) extends Def[ArraySeq[T]]
  
  /* class interface */
  def arrayseq_obj_new[T: Manifest](length: Exp[Int]) = reflectMutable(ArraySeqNew[T](length)(manifest[ArraySeqImpl[T]]))
  def arrayseq_obj_new_range(length: Exp[Int]) = reflectMutable(ArraySeqNewRange(length)(manifest[ArraySeqImpl[Int]]))
  def arrayseq_obj_new_fromarray[T: Manifest](arr: Exp[Array[T]]): Exp[ArraySeq[T]] = reflectMutable(ArraySeqNewFromArray(arr)(manifest[ArraySeqImpl[T]]))
  def arrayseq_obj_new_fromarraybuffer[T: Manifest](arr: Exp[collection.mutable.ArrayBuffer[T]]): Exp[ArraySeq[T]] = reflectMutable(ArraySeqNewFromArrayBuffer(arr)(manifest[ArraySeqImpl[T]]))
  
  /* can-build rules */
  implicit def arraySeqCanBuild[T: Manifest, S: Manifest] = new CanBuild[ArraySeq[T], S, ArraySeq[S]] {
    // TODO: why doesn't this implicit conversion get resolved automatically? It is available in the current scope without a prefix.
    def alloc(source: Exp[ArraySeq[T]]) = ArraySeq[S](arrayseqrep2traversableops(source).size)
    def emptyAlloc(source: Exp[ArraySeq[T]]) = ArraySeq[S](Const(0))
    def emitterFactory(source: Exp[ArraySeq[T]]) = arraySeqEmitterFactory[T]
    def noPrealloc = false
  }
  
}


trait ScalaGenArraySeqOps extends ScalaGenSeqOps {
  val IR: ArraySeqOpsExp
  import IR._
  
  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    // these are the ops that call through to the underlying real data structure
    case v @ ArraySeqNew(length) => emitValDef(sym, "new " + remap(v.mV) + "(" + quote(length) + ")")
    case v @ ArraySeqNewRange(length) => emitValDef(sym, "ArraySeqImpl.range(" + quote(length) + ")")
    case v @ ArraySeqNewFromArray(arr) => emitValDef(sym, "ArraySeqImpl.fromArray(" + quote(arr) + ")")
    case v @ ArraySeqNewFromArrayBuffer(ab) => emitValDef(sym, "ArraySeqImpl.fromArrayBuffer(" + quote(ab) + ")")
    case _ => super.emitNode(sym, rhs)
  }
  
}


