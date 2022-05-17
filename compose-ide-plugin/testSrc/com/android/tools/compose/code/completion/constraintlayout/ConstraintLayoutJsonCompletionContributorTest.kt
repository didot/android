/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.code.completion.constraintlayout

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.json.json5.Json5FileType
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class ConstraintLayoutJsonCompletionContributorTest {
  // TODO(b/207030860): Change test class to 'LightPlatformCodeInsightFixture4TestCase' once/if we remove the Compose requirement

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setup() {
    StudioFlags.COMPOSE_CONSTRAINTLAYOUT_COMPLETION.override(true)
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
  }

  @After
  fun teardown() {
    StudioFlags.COMPOSE_CONSTRAINTLAYOUT_COMPLETION.clearOverride()
  }

  @Test
  fun completeConstraintSetFields() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {},
            id2: {},
            id3: {}
          },
          end: {
            id1: {},
            i$caret
          }
        }
      }
    """.trimIndent())
    val items = myFixture.lookupElementStrings!!
    assertThat(items).hasSize(2)
    assertThat((items[0])).isEqualTo("id2")
    assertThat((items[1])).isEqualTo("id3")
  }

  @Test
  fun completeExtendsValue() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {},
          },
          end: {
            Extends: '$caret'
          }
        }
      }
    """.trimIndent())
    assertThat(myFixture.lookupElementStrings!!).hasSize(1)
    assertThat(myFixture.lookupElementStrings!![0]).isEqualTo("start")
  }

  @Test
  fun completeConstraintBlockFields() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {
              start: [], left: [], right: [],
              $caret
            }
          }
        }
      }
    """.trimIndent())
    val lookupElements = myFixture.lookupElementStrings!!
    assertThat(lookupElements).hasSize(19)
    assertThat(lookupElements).containsNoDuplicates()

    assertThat(lookupElements).doesNotContain("start")
    assertThat(lookupElements).doesNotContain("left")
    assertThat(lookupElements).doesNotContain("right")
  }

  @Test
  fun completeDimensionBehaviors() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {
              width: '$caret'
            }
          }
        }
      }
    """.trimIndent())
    val lookupElements = myFixture.lookupElementStrings!!

    assertThat(lookupElements).hasSize(4)
    assertThat(lookupElements).containsExactly("spread", "wrap", "preferWrap", "parent")
  }

  @Test
  fun completeVisibilityModes() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {
              visibility: '$caret'
            }
          }
        }
      }
    """.trimIndent())
    val lookupElements = myFixture.lookupElementStrings!!

    assertThat(lookupElements).hasSize(3)
    assertThat(lookupElements).containsExactly("visible", "invisible", "gone")
  }

  @Test
  fun completeConstraintIdsInArray() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {
              start: ['$caret', 'start', 0]
            },
            id2: {},
            id3: {}
          }
        }
      }
    """.trimIndent())
    val lookupElements = myFixture.lookupElementStrings!!

    assertThat(lookupElements).hasSize(3)
    assertThat(lookupElements).containsExactly("id2", "id3", "parent")
  }

  @Test
  fun completeConstraintIdsInSpecialAnchors() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {
              center: '$caret'
            },
            id2: {},
            id3: {}
          }
        }
      }
    """.trimIndent())
    val lookupElements = myFixture.lookupElementStrings!!

    assertThat(lookupElements).hasSize(3)
    assertThat(lookupElements).containsExactly("id2", "id3", "parent")
  }

  @Test
  fun completeAnchorsInConstraintArray() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {
              start: ['parent', '$caret', 0]
            }
          }
        }
      }
    """.trimIndent())
    val lookupElements1 = myFixture.lookupElementStrings!!
    assertThat(lookupElements1).hasSize(4)
    assertThat(lookupElements1).containsExactly("end", "left", "right", "start")

    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {
              top: ['parent', '$caret', 0]
            }
          }
        }
      }
    """.trimIndent())
    val lookupElements2 = myFixture.lookupElementStrings!!
    assertThat(lookupElements2).hasSize(3)
    assertThat(lookupElements2).containsExactly("top", "bottom", "baseline")
  }

  @Test
  fun constraintAnchorHandlerResult() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {
              star$caret
            }
          }
        }
      }
    """.trimIndent())
    myFixture.checkResult(
      // language=JSON5
      """
      {
        ConstraintSets: {
          start: {
            id1: {
              start: ['', '', 0],
            }
          }
        }
      }
      """.trimIndent()
    )
  }

  @Test
  fun completionHandlerResult() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            id1: {},
          },
          end: {
            i$caret
          }
        }
      }
    """.trimIndent())
    myFixture.checkResult(
      //language=JSON5
      """{
  ConstraintSets: {
    start: {
      id1: {},
    },
    end: {
      id1: {
        $caret
      }
    }
  }
}""")

    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            Ext$caret
          }
        }
      }
    """.trimIndent())
    myFixture.checkResult(
      // language=JSON5
      """{
  ConstraintSets: {
    start: {
      Extends: '$caret',
    }
  }
}""")
  }

  @Test
  fun completeTransitionFields() {
    myFixture.completeJson5Text("""
      {
        Transitions: {
          default: {
            from: "start",
            $caret
          }
        }
      }
    """.trimIndent())

    val lookupElements = myFixture.lookupElementStrings!!
    assertThat(lookupElements).containsExactly("to", "KeyFrames", "pathMotionArc", "onSwipe")
  }

  @Test
  fun completeTransitionFromAndTo() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          a: {},
          b: {},
          c: {},
          d: {},
        },
        Transitions: {
          default: {
            from: '$caret',
            to: 'a'
          }
        }
      }
    """.trimIndent())
    assertThat(myFixture.lookupElementStrings!!).containsExactly("a", "b", "c", "d")

    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          e: {},
          f: {},
          g: {},
          h: {},
        },
        Transitions: {
          default: {
            from: 'e',
            to: '$caret'
          }
        }
      }
    """.trimIndent())
    assertThat(myFixture.lookupElementStrings!!).containsExactly("e", "f", "g", "h")
  }

  @Test
  fun completeClearField() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          a: {},
          b: {
            Extends: 'a',
            box: {
              clea$caret
            }
          },
        }
      }
    """.trimIndent())
    // The repeated clear is to autocomplete with all options populated
    assertThat(myFixture.lookupElementStrings!!).containsExactly("clear", "clear")
  }

  @Test
  fun completeClearOptions() {
    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          a: {},
          b: {
            Extends: 'a',
            box: {
              clear: ['$caret'],
            }
          },
        }
      }
    """.trimIndent())
    assertThat(myFixture.lookupElementStrings!!).containsExactly("constraints", "dimensions", "transforms")

    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          a: {},
          b: {
            Extends: 'a',
            box: {
              clear: ['constraints', '$caret'],
            }
          },
        }
      }
    """.trimIndent())
    // 'constraints' options is already populated
    assertThat(myFixture.lookupElementStrings!!).containsExactly("dimensions", "transforms")
  }

  @Test
  fun completeOnSwipeFieldsAndValues() {
    myFixture.completeJson5Text("""
      {
        Transitions: {
          default: {
            onSwipe: {
              anchor: 'a',
              $caret
            }
          }
        }
      }
    """.trimIndent())
    assertThat(myFixture.lookupElementStrings!!).containsExactly("side", "direction", "mode")

    myFixture.completeJson5Text("""
      {
        Transitions: {
          default: {
            onSwipe: {
              side: 'midd$caret',
            }
          }
        }
      }
    """.trimIndent())
    myFixture.checkResult("""
      {
        Transitions: {
          default: {
            onSwipe: {
              side: 'middle',
            }
          }
        }
      }
    """.trimIndent())

    myFixture.completeJson5Text("""
      {
        Transitions: {
          default: {
            onSwipe: {
              direction: 'anticl$caret',
            }
          }
        }
      }
    """.trimIndent())
    myFixture.checkResult("""
      {
        Transitions: {
          default: {
            onSwipe: {
              direction: 'anticlockwise',
            }
          }
        }
      }
    """.trimIndent())

    myFixture.completeJson5Text("""
      {
        Transitions: {
          default: {
            onSwipe: {
              mode: 'sp$caret',
            }
          }
        }
      }
    """.trimIndent())
    myFixture.checkResult("""
      {
        Transitions: {
          default: {
            onSwipe: {
              mode: 'spring',
            }
          }
        }
      }
    """.trimIndent())

    myFixture.completeJson5Text("""
      {
        ConstraintSets: {
          start: {
            a: {},
            b: {},
            c: {}
          },
          end: {
            c: {},
            d: {},
            e: {}
          }
        },
        Transitions: {
          default: {
            onSwipe: {
              anchor: '$caret'
            }
          }
        }
      }
    """.trimIndent())
    assertThat(myFixture.lookupElementStrings!!).containsExactly("parent", "a", "b", "c", "d", "e")
  }
}

private fun CodeInsightTestFixture.completeJson5Text(@Language("JSON5") text: String) {
  configureByText(Json5FileType.INSTANCE, text)
  completeBasic()
}