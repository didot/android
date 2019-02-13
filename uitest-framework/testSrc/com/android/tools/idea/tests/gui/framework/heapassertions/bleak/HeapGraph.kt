/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak

import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayDeque
import java.util.IdentityHashMap
import kotlin.system.measureTimeMillis

// marker interface for BLeak internals, so we can avoid tracking our own objects (this would lead
// to memory consumption exponential in the iteration count, for no useful purpose)
interface DoNotTrace

/** [HeapGraph] represents a slightly-abstracted snapshot of the Java object reference graph.
 * Each node corresponds to a single object, and edges represent references, either real, or
 * abstracted. [Expander]s are responsible for defining the nature of this abstraction.
 */
class HeapGraph(val isInitiallyGrowing: Node.() -> Boolean = { false }): DoNotTrace {

  private val expanderChooser: ExpanderChooser = ExpanderChooser(listOf(
    ArrayObjectIdentityExpander(this),
    ClassStaticsExpander(this),
    DefaultObjectExpander(this)))

  private val objToNode: MutableMap<Any, Node> = IdentityHashMap()
  private val rootNodes: List<Node> = traversalRoots.map{Node(it, true)}
  private val nodes: MutableCollection<Node>
    get() = objToNode.values
  val leakRoots: MutableList<Node> = mutableListOf()

  inner class Node(val obj: Any, isRootNode: Boolean = false): DoNotTrace {
    private val expander = expanderChooser.expanderFor(obj)
    val edges = mutableListOf<Edge>()
    val type: Class<*> = obj.javaClass
    var incomingEdge: Edge? = if (isRootNode) Edge(this, this, expander.RootLoopbackLabel()) else null
    val children: List<Node>
      get() = edges.map { it.end }
    val degree: Int
      get() = edges.size
    var mark = 0
    var growing = false
      private set
    var leakShareDivisor = 0
    private var approximateSize = -1

    init {
      objToNode[obj] = this
    }

    fun expand() {
      expander.expand(this)
    }

    fun expandCorrespondingEdge(e: Edge) = expander.expandCorrespondingEdge(this, e)

    fun addEdgeTo(obj: Any, label: Expander.Label): Node {
      val e = Edge(this, getOrCreateNode(obj), label)
      edges.add(e)
      return e.end
    }

    // uses an instrumentation agent to compute the (shallow) size of the object represented by this node.
    // This is done lazily, as it is only of interest on the final iteration, and the computation would be
    // wasteful on previous iterations.
    fun getApproximateSize(): Int {
      if (approximateSize == -1) {
        approximateSize = obj.approxSize()
      }
      return approximateSize
    }

    operator fun get(e: Edge) = expander.getChildForLabel(this, e.label)

    // returns a path from a root to this Node, by following incomingEdge references
    fun getPath(isRoot: (Edge) -> Boolean = { it.label is Expander.RootLoopbackLabel }): Path {
      var e = incomingEdge
      val edges = mutableListOf<Edge>()
      while (e != null && !isRoot(e)) {
        edges.add(e)
        e = e.previous()
      }
      return edges.reversed()
    }

    fun markAsGrowing() {
      if (!growing) {
        growing = true
        leakRoots.add(this)
      }
    }

    fun unmarkGrowing() {
      growing = false
    }

    fun getNode(obj: Any?): Node? = if (obj != null) objToNode[obj] else null

    /* The following methods aren't used directly, but might be useful for debugging leaks */
    // trashes marks
    fun isReachableFrom(n: Node, followWeakSoftRefs: Boolean = false): Boolean {
      var found = false
      bfs(roots = listOf(n), followWeakSoftRefs = followWeakSoftRefs) { if (this@Node === this@bfs) found = true; return@bfs }
      return found
    }

    // trashes marks and incomingEdges
    fun shortestPathTo(n: Node, followWeakSoftRefs: Boolean = false): Path? {
      var found = false
      bfs(roots = listOf(this), setIncomingEdges = true, followWeakSoftRefs = followWeakSoftRefs) { if (n === this@bfs) found = true; return@bfs }
      return if(found) n.getPath { it.end === this } else null
    }

    // trashes marks
    fun dominates(target: Node, roots: Collection<Node> = rootNodes, followWeakSoftRefs: Boolean = false): Boolean {
      var found = false
      bfs (roots = roots, followWeakSoftRefs = followWeakSoftRefs, childFilter = { it !== this }) {
        if (this@bfs === target) {
          found = true
          return@bfs
        }
      }
      return found && target.isReachableFrom(this)
    }

    // trashes marks
    fun dominatedNodes(roots: Collection<Node> = rootNodes, followWeakSoftRefs: Boolean = false): List<Node> {
      val dominated = mutableListOf<Node>()
      bfs (roots = roots, followWeakSoftRefs = followWeakSoftRefs, childFilter = { it !== this }) {}
      bfs (clearMarks = false, markValue = 2, roots = listOf(this), followWeakSoftRefs = followWeakSoftRefs, childFilter = { it.mark != 1 }) {
        dominated.add(this)
      }
      return dominated
    }

    fun retainedSize() = dominatedNodes().fold(0) { acc, node -> acc + node.approximateSize }
  }

  fun forEachNode(action: Node.() -> Unit) = nodes.forEach { it.action() }

  fun getOrCreateNode(obj: Any): Node = objToNode[obj] ?: Node(obj)

  fun expandWholeGraph(): HeapGraph {
    withThreadsPaused {
        time("Expanding graph") {
          bfs { expand(); if (isInitiallyGrowing()) markAsGrowing() }
        }
    }
    println("Graph has ${nodes.size} nodes")
    return this
  }

  // note: path may or may not be a path in this graph
  fun getNodeForPath(path: Path, expand: Boolean = false): Node? {
    if (path.isEmpty()) return null // if it's a root, meh
    val correspondingRoot = objToNode[path.root().obj]
    if (correspondingRoot == null) return null
    var node: Node = correspondingRoot
    for (e in path) {
      val child = if (expand) node.expandCorrespondingEdge(e) else node[e]
      if (child != null) {
        node = child
      } else {
        return null
      }
    }
    return node
  }

  private fun markAll(value: Int = 0) = forEachNode { mark = value }

  /** Performs breadth-first search on the graph.
   *
   * @param clearMarks If true, sets all node marks to [markValue]-1
   * @param markValue When a node is encountered, its [mark] is set to [markValue] so it won't be traversed again.
   * @param setIncomingEdges If true, sets each traversed node's [incomingEdge] to the edge that caused it to be added to the queue.
   * @param followWeakSoftRefs Whether to follow weak and soft references.
   * @param childFilter provides an opportunity to restrict the search scope: nodes for which this returns false will not be
   * added to the queue.
   * @param rootNodes The starting points for the search. Defaults to the HeapGraph roots.
   * @param action is executed on each node as it is removed from the queue
   */
  private fun bfs(clearMarks: Boolean = true, markValue: Int = 1, setIncomingEdges: Boolean = false, followWeakSoftRefs: Boolean = true,
                  childFilter: (Node) -> Boolean = { true }, roots: Collection<Node> = rootNodes, action: Node.() -> Unit) {
    if (clearMarks) markAll(markValue - 1)
    if (setIncomingEdges) nodes.forEach { it.incomingEdge = null }
    roots.forEach {it.mark = markValue}
    with (ArrayDeque<Node>()) {
      addAll(roots)
      while (isNotEmpty()) {
        val n = pop()
        n.action()
        for (e in n.edges) {
          val child = e.end
          if (child.mark != markValue && childFilter(child) && !(followWeakSoftRefs && Reference::class.java.isAssignableFrom(child.type))) {
            if (setIncomingEdges && child.incomingEdge == null) child.incomingEdge = e
            add(child)
          }
          child.mark = markValue
        }
      }
    }
  }

  fun propagateGrowing(newGraph: HeapGraph) {
    time("Propagate growing") {
      newGraph.markAll(0)
      val q = ArrayDeque<Pair<Node, Node>>()
      with(q) {
        addAll(rootNodes.zip(newGraph.rootNodes))
        newGraph.rootNodes.forEach { it.mark = 1 }
        while (isNotEmpty()) {
          val (old, new) = pop()
          if (old.growing && old.degree < new.degree) {
            new.markAsGrowing()
          }
          for (e in old.edges) {
            val correspondingNewNode = new[e]
            if (correspondingNewNode != null && correspondingNewNode.mark == 0) {
              correspondingNewNode.mark = 1
              add(e.end to correspondingNewNode)
            }
          }
        }
      }
    }
    println("New graph has ${newGraph.leakRoots.size} potential leak roots")
  }

  fun propagateGrowingIncremental(newGraph: HeapGraph) {
    time("Incremental propagate growing") {
      withThreadsPaused {
        for (leakRoot in leakRoots) {
          val newNode = newGraph.getNodeForPath(leakRoot.getPath(), true)
          if (newNode != null) {
            newNode.expand()  // need to expand fully at the end to figure out how many children there are
            if (leakRoot.degree < newNode.degree) {
              newNode.markAsGrowing()
            }
          }
        }
      }
    }
    println("New graph has ${newGraph.leakRoots.size} potential leak roots")
  }

  private fun removeTroublesomeRoots() {
    leakRoots.filter { it.getPath().signature().isTroublesome() }.map { it.unmarkGrowing() }
    leakRoots.retainAll { it.growing }
  }

  // ObjectNodes in the Disposer tree have references back to the tree, which maintains a map from Disposable
  // objects to their nodes. If there is more than one leak root inside the disposer (which is common), the
  // reported leakShare or retained size will be misleadingly small, as the entire disposer tree will be
  // reachable from the other leak root. The "troublesome roots" mechanism is insufficient here, since that
  // whitelists the roots. To get around this, we sever the edge for ObjectTree.myObject2NodeMap before
  // ranking the leaks.
  private fun removeDisposerObject2NodeMapEdge() {
    val disposerTree = instancesOf("com.intellij.openapi.util.objectTree.ObjectTree")[0]
    disposerTree.edges.find { "myObject2NodeMap" in it.label.signature() }?.delete()
  }

  enum class LeakRankingStrategy {
    LEAK_SHARE, RETAINED_SIZE;
  }

  fun rankLeakRoots(): List<Pair<Node, Double>> = when (LEAK_RANKING_STRATEGY) {
    LeakRankingStrategy.LEAK_SHARE -> rankLeakRootsByLeakShare()
    LeakRankingStrategy.RETAINED_SIZE -> rankLeakRootsByRetainedSize()
  }

  private fun rankLeakRootsByLeakShare(): List<Pair<Node, Double>> {
    val roots = mutableListOf<Pair<Node, Double>>()
    removeTroublesomeRoots()
    removeDisposerObject2NodeMapEdge()
    time("Computing leakShare") {
      // mark all nodes reachable from non-growing nodes with 0, others with -1
      bfs(markValue = 0, followWeakSoftRefs = false, childFilter = { !it.growing }) {}
      var visitId = 0
      for (leakRoot in leakRoots) {
        visitId++
        bfs(roots = listOf(leakRoot), markValue = visitId, clearMarks = false, followWeakSoftRefs = false, childFilter = { it.mark != 0 }) { leakShareDivisor++ }
      }
      for (leakRoot in leakRoots) {
        visitId++
        var leakShare = 0.0
        bfs(roots = listOf(leakRoot), markValue = visitId, clearMarks = false, followWeakSoftRefs = false,
            childFilter = { it.leakShareDivisor != 0 }) { leakShare += getApproximateSize() / leakShareDivisor }
        roots.add(leakRoot to leakShare)
      }
    }
    return roots.sortedBy { it.second }
  }

  private fun rankLeakRootsByRetainedSize(): List<Pair<Node, Double>> {
    val roots = mutableListOf<Pair<Node, Double>>()
    removeTroublesomeRoots()
    removeDisposerObject2NodeMapEdge()
    time("Computing retained sizes of leak roots") {
      bfs(markValue = 0, followWeakSoftRefs = false, childFilter = { !it.growing }) {}
      var visitId = 0
      for (leakRoot in leakRoots) {
        var retainedSize = 0.0
        visitId++
        bfs(roots = leakRoots.minus(leakRoot), markValue = visitId, clearMarks = false, followWeakSoftRefs = false, childFilter = { it.mark != 0 }) {}
        visitId++
        bfs(roots = listOf(leakRoot), markValue = visitId, clearMarks = false, followWeakSoftRefs = false, childFilter = { it.mark != 0 && it.mark != visitId-1 }) { retainedSize += getApproximateSize() }
        roots.add(leakRoot to retainedSize)
      }
    }
    return roots.sortedBy { it.second }
  }

  fun List<Node>.anyReachableFrom(roots: List<Node>): Boolean {
    var found = false
    bfs(roots = roots, setIncomingEdges = true) { if (this@bfs in this@anyReachableFrom) found = true; return@bfs }
    return found
  }

  fun computeIncomingEdges(followWeakSoftRefs: Boolean = true): Map<Node, List<Edge>> {
    val incomingEdgeMap = IdentityHashMap<Node, MutableList<Edge>>()
    nodes.forEach { incomingEdgeMap[it] = mutableListOf() }
    for (n in nodes) {
      for (e in n.edges) {
        if (e.label !is Expander.RootLoopbackLabel && (followWeakSoftRefs || e.isStrong())) {
          incomingEdgeMap[e.end]?.add(e)
        }
      }
    }
    return incomingEdgeMap
  }

  fun instancesOf(klass: Class<*>) = nodes.filter { it.type === klass }
  fun instancesOf(className: String) = nodes.filter { it.type.name == className }

  companion object {
    val jniHelper: BleakHelper = if (System.getProperty("bleak.jvmti.enabled") == "true") JniBleakHelper() else JavaBleakHelper()
    val traversalRoots = jniHelper.traversalRoots()
    val LEAK_RANKING_STRATEGY = LeakRankingStrategy.valueOf(System.getProperty("bleak.leak.ranking.strategy") ?: "LEAK_SHARE")

    private val objSizeMethod: Method? = try {
      Class.forName(
        "com.android.tools.idea.tests.gui.framework.heapassertions.bleak.agents.ObjectSizeInstrumentationAgent", true,
        ClassLoader.getSystemClassLoader()).getMethod("getObjectSize", Any::class.java)
    } catch (e: ClassNotFoundException) {
      null
    }

    fun Any?.approxSize(): Int {
      return (objSizeMethod?.invoke(null, this) as? Long)?.toInt() ?: 0
    }

    fun withThreadsPaused(action: () -> Unit) {
      jniHelper.pauseThreads()
      action()
      jniHelper.resumeThreads()
    }
  }
}

class Edge(val start: Node, val end: Node, val label: Expander.Label): DoNotTrace {
  init {
    if (end.incomingEdge == null) end.incomingEdge = this
  }
  // the signature is only used for whitelisting
  fun signature(): String =
    if (label is DefaultObjectExpander.FieldLabel && (label.field.modifiers and Modifier.STATIC) != 0) {
      label.field.declaringClass.name + "#" + label.signature()
    } else {
      start.type.name + "#" + label.signature()
    }

  fun previous(): Edge? = start.incomingEdge
  fun isWeak() = start.obj is WeakReference<*>
  fun isSoft() = start.obj is SoftReference<*>
  fun isStrong() = !(isWeak() || isSoft())

  fun delete() {
    start.edges.remove(this)
  }
}

private fun time (description: String, action: () -> Unit) = println("$description took ${measureTimeMillis(action)}ms")

typealias Signature = List<String>
typealias Path = List<Edge>
fun Path.root() = first().start
fun Path.tip() = last().end
fun Path.signature() = map{it.signature()}.plus(tip().type.name)
fun Path.verboseSignature() = map{"${it.signature()}: ${try {it.end.obj.toString().take(90)} catch (e: Exception) {"[EXCEPTION]"}}"}.plus(tip().type.name)

private fun String.typePart(): String = substringBefore('#')
private fun String.labelPart(): String = substringAfter('#')
fun Signature.entry(i: Int): String {
  val realIndex = if (i < 0) size + i else i
  if (realIndex in 0 until size) return get(realIndex)
  return ""
}
fun Signature.type(index: Int) = entry(index).typePart()
fun Signature.label(index: Int) = entry(index).labelPart()
fun Signature.penultimateType() = if (size <= 1) "" else type(size-2)
fun Signature.firstLabel() = if (size == 0) "" else label(0)
fun Signature.firstType() = if (size == 0) "" else type(0)
fun Signature.lastLabel() = if (size <= 1) "" else label(size - 2)
fun Signature.lastType() = if (size == 0) "" else type(size - 1)
fun Signature.anyTypeIs(type: String) = any{ it.typePart() == type }
fun Signature.anyTypeContains(typeFragment: String) = any{ it.typePart().contains(typeFragment) }
fun Signature.anyLabelIs(label: String) = any{ it.typePart() == label }
fun Signature.anyLabelContains(labelFragment: String) = any{ it.typePart().contains(labelFragment) }

