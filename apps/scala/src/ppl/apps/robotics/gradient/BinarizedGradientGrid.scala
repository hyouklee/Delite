package ppl.apps.robotics.gradient

import ppl.dsl.optiml._
import ppl.dsl.optiml.datastruct.scala._
import ppl.delite.framework.DeliteApplication

trait BinarizedGradientGridFuncs {
  this: OptiMLExp with BinarizedGradientPyramidFuncs with BinarizedGradientTemplateFuncs =>

  // The radius of the template
  val template_radius_ = unit(15)

  // Threshold for accepting a template match (1 is perfect)
  val accept_threshold_ = unit(0.82f)

  // Ignore gradients with lower magnitude
  val magnitude_threshold_ = unit(200)

  // Fraction overlap between two detections above which one of them is suppressed
  val fraction_overlap_ = unit(0.6f)

  val borderPixels = unit(5)

  // Runs the object detection of the current image.
  def detectAllObjects(all_templates: Rep[Vector[(String, Vector[BinarizedGradientTemplate])]], image: Rep[GrayscaleImage]) = {
    val img_gray = image // assuming image is single-channel. Needs to be made such if not.

    val (mag: Rep[Matrix[Float]], phase: Rep[Matrix[Float]]) = t2(repGrayscaleImageToGrayscaleImageOps(img_gray).gradients(true))
    val binGrad = binarizeGradients(mag, phase)
    val cleanGrad = gradMorphology(binGrad)

    val pyr = makePyramid(cleanGrad)

    val all_detections = all_templates.flatMap { t =>
      val (name, templates) = t2(t)
      println("Name: " + name)
      println("Templates: " + templates.length)
      val detections = detectSingleObject(name, getIndex(pyr, pyr.fixedLevelIndex), templates, template_radius_, pyr.fixedLevelIndex, accept_threshold_)
      println("Detections: " + detections.length)
      detections
    }
    val filteredDetections = nonMaxSuppress(all_detections, fraction_overlap_)
    println("Total detections: " + filteredDetections.length)
  }

  //Run detection for this object class.
  def detectSingleObject(name: Rep[String], gradSummary: Rep[GrayscaleImage], templates: Rep[Vector[BinarizedGradientTemplate]], template_radius: Rep[Int], level: Rep[Int], accept_threshold: Rep[Float]): Rep[Vector[BiGGDetection]] = {
    (borderPixels :: gradSummary.numRows - borderPixels).flatMap { y =>
      (borderPixels :: gradSummary.numCols - borderPixels).flatMap { x =>
        searchTemplates(name, gradSummary, x, y, template_radius, level, accept_threshold, templates)
      }
    }
  }

  def searchTemplates(name: Rep[String], gradSummary: Rep[GrayscaleImage], x: Rep[Int], y: Rep[Int], template_radius: Rep[Int], level: Rep[Int], accept_threshold: Rep[Float], templates: Rep[Vector[BinarizedGradientTemplate]]): Rep[Vector[BiGGDetection]] = {
    val reduction_factor = Math.pow(2, level).asInstanceOfL[Int]//(1 << level)
    val crt_template = fillTemplateFromGradientImage(gradSummary, x, y, template_radius, level)
    (unit(0) :: templates.length).flatMap { j =>
      val res = score(templates(j), crt_template, accept_threshold)
      if (res > accept_threshold) {
        val bbox = templates(j).rect
        val roi = Rect((reduction_factor * x - bbox.width / 2).asInstanceOfL[Int], (reduction_factor * y - bbox.height / 2).asInstanceOfL[Int], bbox.width, bbox.height)
        val out = Vector[BiGGDetection](1, true)
        out(0) = BiGGDetection(name, res, roi, null, j, x, y, templates(j), crt_template)
        out
      }
      else {
        Vector[BiGGDetection]()
      }
    }
  }

  // Construct a template from a region of a gradient summary image.
  def fillTemplateFromGradientImage(gradSummary: Rep[GrayscaleImage], xc: Rep[Int], yc: Rep[Int], r: Rep[Int], level: Rep[Int]): Rep[BinarizedGradientTemplate] = {
    val span = 2 * r
    val tpl = BinarizedGradientTemplate(r, null, null, level, Vector[Int](span * span, false), IndexVector(0), null, null, null)

    //Bear with me, we have to worry a bit about stepping off the image boundaries:
    val (xstart, xoffset) = t2(if (xc - r < 0) (unit(0), r - xc) else (xc - r, unit(0)))
    val xend = if (xc + r > gradSummary.numCols) gradSummary.numCols else xc + r
    val (ystart, yoffset) = t2(if (yc - r < 0) (unit(0), r - yc) else (yc - r, unit(0)))
    val yend = if (yc + r > gradSummary.numRows) gradSummary.numRows else yc + r

    //Fill the binary gradients
    var y = ystart
    while (y < yend) {
      val imageRow = gradSummary.getRow(y)
      var x = xstart
      while (x < xend) {
        var index = (yoffset + y - ystart) * span + (xoffset + x - xstart) //If this were an image patch, this is the offset to it
        tpl.binary_gradients(index) = imageRow(x)
        if (imageRow(x) > 0) {
          //Record where gradients are
          tpl.match_list += index
        }
        x += 1
      }
      y += 1
    }
    tpl
  }

  //Turn mag and phase into a binary representation of 8 gradient directions.
  def binarizeGradients(mag: Rep[Matrix[Float]], phase: Rep[Matrix[Float]]): Rep[GrayscaleImage] = {
    GrayscaleImage((mag zip phase) {(a,b) => {
      if (a >= magnitude_threshold_) {
          var angle = b
          if (angle >= unit(180)) {
            angle += unit(-180) //Ignore polarity of the angle
          }
          (Math.pow(unit(2), (angle.asInstanceOfL[Float] / unit(180.0 / 8))).asInstanceOfL[Int])
        }
      else 0
    }})
  }

  // Filter out noisy gradients via non-max suppression in a 3x3 area.
  def gradMorphology(binaryGradient: Rep[GrayscaleImage]): Rep[GrayscaleImage] = {
    //Zero the borders -- they are unreliable
//    binaryGradient.getRow(0) = 0
//    binaryGradient.getRow(binaryGradient.numRows - 1) = 0
//    binaryGradient.getCol(0) = 0
//    binaryGradient.getCol(binaryGradient.numCols - 1) = 0
//    for (x <- 0 until cols) {
//      binaryGradient.data(0, x) = 0
//      binaryGradient.data(rows - 1, x) = 0
//    }
//    for (y <- 1 until rows - 1) {
//      binaryGradient.data(y, 0) = 0
//      binaryGradient.data(y, cols - 1) = 0
//    }
    binaryGradient.getRow(0).mmap { e => 0}
    binaryGradient.getRow(binaryGradient.numRows - 1).mmap {e => 0}
    binaryGradient.getCol(0).mmap { e => 0}
    binaryGradient.getCol(binaryGradient.numCols - 1).mmap {e => 0}

    // non-max suppression over a 3x3 stencil throughout the entire binaryGradient image
    // (Each pixel location contains just one orientation at this point)
    repGrayscaleImageToGrayscaleImageOps(binaryGradient).windowedFilter (3, 3) { slice /*3x3 Matrix[T]*/ =>
      // for each element, pick the most frequently occurring gradient direction if it's at least 2; otherwise pick 0(no direction)
      val histogram = Vector[Int](255)
      // TODO: Make this a scan-like op once supported
      var row = unit(0)
      while (row < slice.numRows) {
        var col = unit(0)
        while (col < slice.numCols) {
          //histogram(slice(row, col)) += 1
          histogram(slice(row,col)) = histogram(slice(row,col))+1
          col += 1
        }
        row += 1
      }
      var i = unit(1)
      var max = histogram(0)
      var maxIndex = unit(0)
      while (i < histogram.length) {
        if (histogram(i) > max) {
          max = histogram(i)
          maxIndex = i
        }
        i += 1
      }
      if (max >= 2) maxIndex else unit(0)
    }
  }

  // Determines if two rectangles intersect (true = intersects)
  def intersect(a: Rep[Rect], b: Rep[Rect]): Rep[Boolean] = {
    ((a.x < (b.x + b.width)) && ((a.x + a.width) > b.x) && ((a.y + a.height) > b.y) && (a.y < (b.y + b.height)))
  }

  // Computes the fraction of the intersection of two rectangles with respect to the total area of the rectangles.
  def rectFractOverlap(a: Rep[Rect], b: Rep[Rect]): Rep[Float] = {
    if (intersect(a, b)) {
      val total_area: Rep[Float] = b.height * b.width + a.width * a.height
      val left = if (a.x > b.x) a.x else b.x
      val top = if (a.y > b.y) a.y else b.y
      val right = if (a.x + a.width < b.x + b.width) a.x + a.width else b.x + b.width
      val width = right - left
      val bottom = if (a.y + a.height < b.y + b.height) a.y + a.height else b.y + b.height
      val height = bottom - top
      unit(2.0f) * height * width / (total_area + 0.000001f) //Return the fraction of intersection
    } else {
      unit(0.0f)
    }
  }

  // Suppress overlapping rectangles to be the rectangle with the highest score
  // detections: vector of detections to work with
  // overlapThreshold: what fraction of overlap between 2 rectangles constitutes overlap
  def nonMaxSuppress(detections: Rep[Vector[BiGGDetection]], overlapThreshold: Rep[Float]): Rep[Vector[BiGGDetection]] = {
    var len = detections.length

//    detections filter { d1 =>
//      var isMax = true
//      var d2Index = 0
//      while (d2Index < detections.length) {
//        val d2 = detections(d2Index)
//        if (d1 != d2) {
//          val measuredOverlap = rectFractOverlap(d1.roi, d2.roi)
//          if (measuredOverlap > overlapThreshold) {
//            if (d1.score < d2.score) {
//              isMax = false
//            }
//          }
//        }
//        d2Index += 1
//      }
//      isMax
//    }
    var i = unit(0)
    while (i < len - 1) {
      var j = i + 1
      var iMoved = false
      while (j < len && iMoved == false) {
        val measured_frac_overlap = rectFractOverlap(detections(i).roi, detections(j).roi)
        if (measured_frac_overlap > overlapThreshold) {
          if (detections(i).score >= detections(j).score) {
            val temp = detections(len - 1)
            detections(len - 1) = detections(j)
            detections(j) = temp
            len = len - 1
            j = j - 1
          }
          else {
            val temp = detections(len - 1)
            detections(len - 1) = detections(i)
            detections(i) = temp
            len = len - 1
            i = i - 1
            iMoved = true
          }
        }
        j += 1
      }
      i += 1
    }
    detections.take(len)
  }
}