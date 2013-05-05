package com.faacets
package perm
package wreath

import com.faacets.math.{ind2sub, sub2ind}

class InhImprimitiveAction[AE <: FiniteElement[AE], HE <: PermElement[HE]](val ba: Array[Action[AE, Perm]]) extends InhWreathAction[AE, HE] {
  def apply(we: InhWreathElement[AE, HE]) = {
    val dest = (ba zip we.ke.arr).map(Function.tupled( (b, w) => b(w) ))
    val dims = dest.map(_.size)
    val start = dims.scanLeft(0)(_+_)
    val dim = dims.sum
    def image(k: Dom) = {
      val i = start.zipWithIndex.find(_._1 > k._0).get._2 - 1
      val o = k._0 - start(i)
      val i1 = we.he.image(Dom._0(i))._0
      val o1 = dest(i).image(Dom._0(o))._0
      Dom._0(start(i1) + o1)
    }
    new Perm(Array.tabulate(dim) ( k => image(Dom._0(k))._0 ))
  }
}
