package io.github.sgrpg.ceye
import org.junit.Assert.*
import org.junit.Test
class SelectionTest {
    private val ids=(0..20).map{it.toLong()}
    @Test fun rangePreservesPriorSelection(){assertEquals(setOf(10L,1L,2L,3L),selectionRange(ids,setOf(10L),1L,3L,true))}
    @Test fun reverseDragRemovesRange(){assertEquals(setOf(0L,9L),selectionRange(ids,setOf(0L,1L,2L,3L,9L),3L,1L,false))}
    @Test fun shrinkingRangeRestoresBaseline(){val baseline=setOf(10L);assertEquals(setOf(10L,1L,2L),selectionRange(ids,baseline,1L,2L,true))}
    @Test fun missingAnchorDoesNotCorruptSelection(){assertEquals(setOf(8L),selectionRange(ids,setOf(8L),99L,1L,true))}
}
