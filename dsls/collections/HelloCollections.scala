



import ppl.delite.framework.collections._



trait HelloCollections extends CollectionsApplication {
  def main() {
    // val xs = ArraySeq[Int](6)
    // println("hello array: " + xs.size)
    // println("Now printing in foreach.")
    // for (x <- xs) println(x)
    // println("Now mapping and printing in foreach.")
    // val ys = xs.map(_ + 1)
    // for (y <- ys) println(y)
    // println("Now some filtering.")
    // val zs = ys.filter(_ % 2 == 1)
    // println("Elements left after filter: " + zs.size)
    // for (z <- zs) println(z)
    
    val ms = HashMap[Int, String]()
    ms.map(kv => kv)
  }
}


object HelloCollectionsRunner extends CollectionsApplicationRunner with HelloCollections





