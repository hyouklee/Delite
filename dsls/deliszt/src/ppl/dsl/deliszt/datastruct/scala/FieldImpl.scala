package ppl.dsl.deliszt.datastruct.scala

/**
 * author: Michael Wu (mikemwu@stanford.edu)
 * last modified: 04/24/2011
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

object FieldImpl {
  def ofCell[T:ClassManifest]() : Field[T] = {
    new FieldImpl(new Array[T](Mesh.mesh.ncells))
  }
  
  def ofEdge[T:ClassManifest]() : Field[T] = {
    new FieldImpl(new Array[T](Mesh.mesh.nedges))
  }
  
  def ofFace[T:ClassManifest]() : Field[T] = {
    new FieldImpl(new Array[T](Mesh.mesh.nfaces))
  }
  
  def ofVertex[T:ClassManifest]() : Field[T] = {
    new FieldImpl(new Array[T](Mesh.mesh.nvertices))
  }
  
  def cellWithConst[T:ClassManifest](v: T) : Field[T] = {
    val f = FieldImpl.ofCell[T]()
    f.fill(v)
    f
  }
  
  def edgeWithConst[T:ClassManifest](v: T) : Field[T] = {
    val f = FieldImpl.ofEdge[T]()
    f.fill(v)
    f
  }
  
  def faceWithConst[T:ClassManifest](v: T) : Field[T] = {
    val f = FieldImpl.ofFace[T]()
    f.fill(v)
    f
  }
  
  def vertexWithConst[T:ClassManifest](v: T) : Field[T] = {
    val f = FieldImpl.ofVertex[T]()
    f.fill(v)
    f
  }
}

class FieldImpl[@specialized T: ClassManifest](val data : Array[T]) extends Field[T] {
  def apply(idx: Int) = data(Mesh.internal(idx))
  def update(idx: Int, x: T) = {
    data(Mesh.internal(idx)) = x
  }
  def size = data.length
}