package ppl.delite.runtime.graph.targets

import ppl.delite.runtime.graph.ops.DeliteOP

/**
 * Author: Kevin J. Brown
 * Date: Dec 4, 2010
 * Time: 10:48:52 PM
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

final class CudaMetadata {

  val blockSizeX = new OPData
  val blockSizeY = new OPData
  val blockSizeZ = new OPData
  val dimSizeX = new OPData
  val dimSizeY = new OPData
  var inputs: List[OPData] = Nil
  var temps: List[OPData] = Nil
  var tempOps: List[DeliteOP] = Nil
  val outputAlloc = new OPData
  val outputSet = new OPData

  def apply(field: String) = field match {
    case "gpuBlockSizeX" => blockSizeX
    case "gpuBlockSizeY" => blockSizeY
    case "gpuBlockSizeZ" => blockSizeZ
    case "gpuDimSizeX" => dimSizeX
    case "gpuDimSizeY" => dimSizeY
    case "outputAlloc" => outputAlloc
    case other => error("unknown field: " + other)
  }

  def newInput = {
    val in = new OPData
    inputs ::= in
    in
  }

  def newTemp = {
    val temp = new OPData
    temps ::= temp
    temp
  }

  class OPData {

    var func: String = _
    var inputs: List[DeliteOP] = Nil
    var resultType: String = _

  }

}