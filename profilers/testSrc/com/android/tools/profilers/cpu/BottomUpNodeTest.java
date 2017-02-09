/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class BottomUpNodeTest {
  private final static double EPS = 1e-5;

  @Test
  public void testComplexBottomUpNode() {
    List<ExpectedNode> expectedNodes = Arrays.asList(
      new ExpectedNode("Root", 40.0, 30.0),
      new ExpectedNode(":main", 40.0, 30.0),
      new ExpectedNode(":A", 20.0, 10.0),
      new ExpectedNode(":main", 15.0, 5.0),
      new ExpectedNode(":C", 5.0, 5.0),
      new ExpectedNode(":main", 5.0, 5.0),
      new ExpectedNode(":B", 15.0, 0.0),
      new ExpectedNode(":A", 10.0, 0.0),
      new ExpectedNode(":main", 5.0, 0.0),
      new ExpectedNode(":C", 5.0, 0.0),
      new ExpectedNode(":main", 5.0, 0.0),
      new ExpectedNode(":main", 5.0, 0.0),
      new ExpectedNode(":C", 10.0, 5.0),
      new ExpectedNode(":main", 10.0, 5.0)
    );
    traverseAndCheck(createComplexTree(), expectedNodes);
  }

  /**
   * The structure of the tree:
   * main [0..20]
   * -> A [0..10] -> B [2..7] -> C [3..4]
   * -> B [15..20]
   */
  @Test
  public void testNodeHasManyDirectCallers() {
    List<ExpectedNode> expectedNodes = Arrays.asList(
      new ExpectedNode("Root", 20.0, 15.0),
      new ExpectedNode(":main", 20.0, 15.0),
      // Subtree of node "A"
      new ExpectedNode(":A", 10.0, 5.0),
      new ExpectedNode(":main", 10.0, 5.0),

      // Subtree of node "B"
      new ExpectedNode(":B", 10.0, 1.0),
      new ExpectedNode(":A", 5.0, 1.0),
      new ExpectedNode(":main", 5.0, 1.0),
      new ExpectedNode(":main", 5.0, 0.0),
      // Subtree of node "C"
      new ExpectedNode(":C", 1.0, 0.0),
      new ExpectedNode(":B", 1.0, 0.0),
      new ExpectedNode(":A", 1.0, 0.0),
      new ExpectedNode(":main", 1.0, 0.0)
    );

    // Construct the tree
    HNode<MethodModel> root = newHNode("main", 0, 20);
    HNode<MethodModel> childA = newHNode("A", 0, 10);
    root.addHNode(childA);
    root.addHNode(newHNode("B", 15, 20));

    childA.addHNode(newHNode("B", 2, 7));
    childA.getChildren().get(0).addHNode(newHNode("C", 3, 4));

    traverseAndCheck(root, expectedNodes);
  }

  /**
   * The structure of the tree:
   * main [0..20] -> A [0..15] -> B [3..13] -> B [5..10] -> B [5..7]
   */
  @Test
  public void testDirectRecursion() {
    List<ExpectedNode> expectedNodes = Arrays.asList(
      new ExpectedNode("Root", 20.0, 15.0),
      new ExpectedNode(":main", 20.0, 15.0),
      // Subtree of node "A"
      new ExpectedNode(":A", 15.0, 10.0),
      new ExpectedNode(":main", 15.0, 10.0),

      // Subtree of Node "B"
      new ExpectedNode(":B", 10, 0),
      new ExpectedNode(":A", 10.0, 5.0),
      new ExpectedNode(":main", 10.0, 5.0),

      // "B" -> "B"
      new ExpectedNode(":B", 5.0, 0.0),
      // "A" -> "B" -> "B"
      new ExpectedNode(":A", 5.0, 2.0),
      new ExpectedNode(":main", 5.0, 2.0),
      // "B" -> "B" -> "B"
      new ExpectedNode(":B", 2.0, 0.0),
      new ExpectedNode(":A", 2.0, 0.0),
      new ExpectedNode(":main", 2.0, 0.0)
    );
    // Construct the tree
    HNode<MethodModel> root = newHNode("main", 0, 20);
    addChainSubtree(root, newHNode("A", 0, 15), newHNode("B", 3, 13),
                    newHNode("B", 5, 10), newHNode("B", 5, 7));
    traverseAndCheck(root, expectedNodes);
  }

  /**
   * The structure of the tree:
   * main [0..30] -> A [5..25] -> B [5..20] -> A [10..20] -> B [15..16]
   */
  @Test
  public void testIndirectRecursion() {
    List<ExpectedNode> expectedNodes = Arrays.asList(
      new ExpectedNode("Root", 30.0, 20.0),
      new ExpectedNode(":main", 30.0, 20.0),

      // Subtree of the node "A"
      // A
      new ExpectedNode(":A", 20.0, 6.0),
      // main -> A
      new ExpectedNode(":main", 20.0, 15.0),
      // B -> A
      new ExpectedNode(":B", 10.0, 1.0),
      // A -> B -> A
      new ExpectedNode(":A", 10.0, 1.0),
      // main -> A -> B -> A
      new ExpectedNode(":main", 10.0, 1.0),

      // Subtree of the node "B"
      // B
      new ExpectedNode(":B", 15.0, 9.0),
      // A -> B
      new ExpectedNode(":A", 15.0, 9.0),
      // main -> A -> B
      new ExpectedNode(":main", 15.0, 10.0),
      // B -> A -> B
      new ExpectedNode(":B", 1.0, 0.0),
      // A -> B -> A -> B
      new ExpectedNode(":A", 1.0, 0.0),
      // main -> A -> B -> A -> B
      new ExpectedNode(":main", 1.0, 0.0)
    );

    // Construct the tree
    HNode<MethodModel> root = newHNode("main", 0, 30);
    addChainSubtree(root, newHNode("A", 5, 25), newHNode("B", 5, 20),
                    newHNode("A", 10, 20), newHNode("B", 15, 16));

    traverseAndCheck(root, expectedNodes);
  }

  /**
   * The structure of the tree:
   * main [0..40]
   *   -> A [0..25] -> B [0..20] -> C [5..15]
   *   -> D [30..40] -> C [30..35] -> B [30..33]
   */
  @Test
  public void testStaticRecursion() {
    List<ExpectedNode> expectedNodes = Arrays.asList(
      new ExpectedNode("Root", 40.0, 35.0),
      new ExpectedNode(":main", 40.0, 35.0),

      // Subtree of the node "A"
      // A
      new ExpectedNode(":A", 25.0, 20.0),
      // main -> A
      new ExpectedNode(":main", 25.0, 20.0),

      // Subtree of the node "B"
      // B
      new ExpectedNode(":B", 23.0, 10.0),
      // A -> B
      new ExpectedNode(":A", 20.0, 10.0),
      // main -> A -> B
      new ExpectedNode(":main", 20.0, 10.0),
      // C -> B
      new ExpectedNode(":C", 3.0, 0.0),
      // D -> C -> B
      new ExpectedNode(":D", 3.0, 0.0),
      // main -> D -> C -> B
      new ExpectedNode(":main", 3.0, 0.0),

      // Subtree of the node "C"
      // C
      new ExpectedNode(":C", 15.0, 3.0),
      // B -> C
      new ExpectedNode(":B", 10.0, 0.0),
      // A -> B -> C
      new ExpectedNode(":A", 10.0, 0.0),
      // main -> A -> B -> C
      new ExpectedNode(":main", 10.0, 0.0),
      // D -> C
      new ExpectedNode(":D", 5.0, 3.0),
      // main -> D -> C
      new ExpectedNode(":main", 5.0, 3.0),

      // Subtree of the node "D"
      // D
      new ExpectedNode(":D", 10.0, 5.0),
      // main -> D
      new ExpectedNode(":main", 10.0, 5.0)
    );

    HNode<MethodModel> root = newHNode("main", 0, 40);
    addChainSubtree(root, newHNode("A", 0, 25), newHNode("B", 0, 20),
                    newHNode("C", 5, 15));
    addChainSubtree(root, newHNode("D", 30, 40), newHNode("C", 30, 35),
                    newHNode("B", 30, 33));

    traverseAndCheck(root, expectedNodes);
  }

  /**
   * The structure of the tree:
   * main
   *   -> A [0..100]  -> A [0..40]  -> A [0..20]
   *   -> B [21..40]  -> A [25..28]
   *   -> B [45..100] -> A [50..70] -> B [55..65]
   *
   * From the structure of call tree above, we can infer that the top of the call stack looks like
   * (including the timestamps and method name):
   * 0 - A - 20 - A - 21 - B - 25 - A - 28 - B - 40 - A - 45 - B - 50 - A - 55 - B - 65 - A - 70 - B - 100
   */
  @Test
  public void testPartialRangeWithMixedTwoMethods() {
    HNode<MethodModel> root = newHNode("main", 0, 100);

    addChainSubtree(root, newHNode("A", 0, 100), newHNode("A", 0, 40),
                    newHNode("A", 0, 20));
    addChainSubtree(root.getFirstChild(), newHNode("B", 45, 100), newHNode("A", 50, 70),
                    newHNode("B", 55, 65));
    addChainSubtree(root.getFirstChild().getFirstChild(), newHNode("B", 21, 40),
                    newHNode("A", 25, 28));

    BottomUpNode node = new BottomUpNode(root);

    BottomUpNode nodeA = node.getChildren().stream().filter(n -> n.getId().equals(":A")).findAny().orElseThrow(AssertionError::new);
    nodeA.update(new Range(0, 100));
    assertEquals(100, nodeA.getTotal(), EPS);
    assertEquals(61, nodeA.getChildrenTotal(), EPS);

    nodeA.update(new Range(21, 40));
    assertEquals(19, nodeA.getTotal(), EPS);
    assertEquals(16, nodeA.getChildrenTotal(), EPS);

    nodeA.update(new Range(66, 71));
    assertEquals(5, nodeA.getTotal(), EPS);
    assertEquals(1, nodeA.getChildrenTotal(), EPS);
  }

  private static void traverseAndCheck(HNode<MethodModel> root, List<ExpectedNode> expectedNodes) {
    List<BottomUpNode> traverseOrder = new ArrayList<>();
    traverse(new BottomUpNode(root), traverseOrder);

    Range viewRange = new Range(root.getStart(), root.getEnd());
    traverseOrder.forEach(node -> node.update(viewRange));
    checkTraverseOrder(expectedNodes, traverseOrder);
  }

  private static void addChainSubtree(HNode<MethodModel> root, HNode<MethodModel>... chainNodes) {
    HNode<MethodModel> last = root;
    for (HNode<MethodModel> node : chainNodes) {
      last.addHNode(node);
      last = node;
    }
  }

  private static void traverse(BottomUpNode node, List<BottomUpNode> traverseOrder) {
    node.buildChildren();
    traverseOrder.add(node);
    for (BottomUpNode child : node.getChildren()) {
      traverse(child, traverseOrder);
    }
  }

  private static void checkTraverseOrder(List<ExpectedNode> expected, List<BottomUpNode> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); ++i) {
      BottomUpNode node = actual.get(i);
      assertEquals(expected.get(i).myId, node.getId());
      assertEquals(expected.get(i).myTotal, node.getTotal(), EPS);
      assertEquals(expected.get(i).myChildrenTotal, node.getChildrenTotal(), EPS);
    }
  }

  @NotNull
  private static HNode<MethodModel> newHNode(String method, long start, long end) {
    return new HNode<>(new MethodModel(method), start, end);
  }

  @NotNull
  public static HNode<MethodModel> createComplexTree() {
    HNode<MethodModel> root = newHNode("main", 0, 40);
    HNode<MethodModel> childA = newHNode("A", 0, 15);
    HNode<MethodModel> childC = newHNode("C", 20, 30);
    HNode<MethodModel> childB = newHNode("B", 35, 40);

    root.addHNode(childA);
    root.addHNode(childC);
    root.addHNode(childB);
    childA.addHNode(newHNode("B", 5, 10));
    childC.addHNode(newHNode("A", 20, 25));
    childC.getChildren().get(0).addHNode(newHNode("B", 20, 25));
    return root;
  }

  private static class ExpectedNode {
    private String myId;
    private double myTotal;
    private double myChildrenTotal;

    private ExpectedNode(String id, double total, double childrenTotal) {
      myId = id;
      myTotal = total;
      myChildrenTotal = childrenTotal;
    }
  }
}