package ppl.dsl.optiml.datastruct.scala

class TrainingSetImpl[T:Manifest,L:Manifest](xs: Matrix[T], var _labels: Labels[L]) extends MatrixImpl[T](0,0) with TrainingSet[T,L] {
  lazy val transposed = transpose

  def labels = _labels

  private def transpose = {
    val out = new MatrixImpl[T](numFeatures, numSamples)
    for (i <- 0 until numSamples) {
      for (j <- 0 until numFeatures) {
        out(j,i) = this(i,j)
      }
    }
	out
    //new TrainingSetImpl[T,L](out, _labels)
  }

  // not a deep copy, use with care
  _data = xs.data
  _numRows = xs.numRows
  _numCols = xs.numCols

}
