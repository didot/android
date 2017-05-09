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
package com.android.tools.idea.lang.roomSql.parser

import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.*
import com.google.common.truth.Truth
import com.intellij.psi.TokenType
import com.intellij.psi.TokenType.BAD_CHARACTER
import com.intellij.psi.tree.IElementType
import junit.framework.TestCase

class RoomSqlLexerTest : TestCase() {
  private fun assertTokenTypes(input: String, vararg expectedTokenTypes: Pair<String, IElementType>) {
    val lexer = RoomSqlLexer()
    val actualTokenTypes = mutableListOf<Pair<String, IElementType>>()
    lexer.start(input)
    while (lexer.tokenType != null) {
      actualTokenTypes.add(lexer.tokenText to lexer.tokenType!!)
      lexer.advance()
    }

    Truth.assertThat(actualTokenTypes).containsExactlyElementsIn(expectedTokenTypes.asIterable())
  }

  private val SPACE = " " to TokenType.WHITE_SPACE

  fun testSimpleQueries() {
    assertTokenTypes(
        "select foo from bar",
        "select" to SELECT,
        SPACE,
        "foo" to IDENTIFIER,
        SPACE,
        "from" to FROM,
        SPACE,
        "bar" to IDENTIFIER)

    assertTokenTypes("select -22", "select" to SELECT, SPACE, "-" to MINUS, "22" to NUMERIC_LITERAL)
  }

  fun testWhitespace() {
    val input = """
        select a,b
        from foo
        where baz >= :arg""".trimIndent()

    assertTokenTypes(
        input,
        "select" to SELECT,
        SPACE,
        "a" to IDENTIFIER,
        "," to COMMA,
        "b" to IDENTIFIER,
        "\n" to TokenType.WHITE_SPACE,
        "from" to FROM,
        SPACE,
        "foo" to IDENTIFIER,
        "\n" to TokenType.WHITE_SPACE,
        "where" to WHERE,
        SPACE,
        "baz" to IDENTIFIER,
        SPACE,
        ">=" to GTE,
        SPACE,
        ":arg" to PARAMETER_NAME)
  }

  fun testComments() {
    assertTokenTypes(
        "select 17 -- hello",
        "select" to SELECT,
        SPACE,
        "17" to NUMERIC_LITERAL,
        SPACE,
        "-- hello" to LINE_COMMENT)

    assertTokenTypes(
        "select 17 -- hello\nfrom bar",
        "select" to SELECT,
        SPACE,
        "17" to NUMERIC_LITERAL,
        SPACE,
        "-- hello" to LINE_COMMENT,
        "\n" to TokenType.WHITE_SPACE,
        "from" to FROM,
        SPACE,
        "bar" to IDENTIFIER)

    assertTokenTypes(
        "select /* hello */ 17",
        "select" to SELECT,
        SPACE,
        "/* hello */" to COMMENT,
        SPACE,
        "17" to NUMERIC_LITERAL)

    assertTokenTypes(
        "select /* hello",
        "select" to SELECT,
        SPACE,
        "/* hello" to COMMENT)
  }

  fun testIdentifiers() {
    assertTokenTypes(
        "select * from _table",
        "select" to SELECT,
        SPACE,
        "*" to STAR,
        SPACE,
        "from" to FROM,
        SPACE,
        "_table" to IDENTIFIER)

    assertTokenTypes(
        "select null, nulls, current_time, current_times, current_timestamp",
        "select" to SELECT,
        SPACE,
        "null" to NULL,
        "," to COMMA,
        SPACE,
        "nulls" to IDENTIFIER,
        "," to COMMA,
        SPACE,
        "current_time" to CURRENT_TIME,
        "," to COMMA,
        SPACE,
        "current_times" to IDENTIFIER,
        "," to COMMA,
        SPACE,
        "current_timestamp" to CURRENT_TIMESTAMP
    )

    assertTokenTypes(
        "select :P1, :_p2",
        "select" to SELECT,
        SPACE,
        ":P1" to PARAMETER_NAME,
        "," to COMMA,
        SPACE,
        ":_p2" to PARAMETER_NAME)

    assertTokenTypes(
        "select :P1, ? from foo",
        "select" to SELECT,
        SPACE,
        ":P1" to PARAMETER_NAME,
        "," to COMMA,
        SPACE,
        "?" to BAD_CHARACTER, // We don't support unnamed parameters.
        SPACE,
        "from" to FROM,
        SPACE,
        "foo" to IDENTIFIER)

    assertTokenTypes(
        "select [table].[column] from [database].[column]",
        "select" to SELECT,
        SPACE,
        "[table]" to BRACKET_LITERAL,
        "." to DOT,
        "[column]" to BRACKET_LITERAL,
        SPACE,
        "from" to FROM,
        SPACE,
        "[database]" to BRACKET_LITERAL,
        "." to DOT,
        "[column]" to BRACKET_LITERAL)

    assertTokenTypes(
        "select 11*11.22e33+11e+22-11.22e-33",
        "select" to SELECT,
        SPACE,
        "11" to NUMERIC_LITERAL,
        "*" to STAR,
        "11.22e33" to NUMERIC_LITERAL,
        "+" to PLUS,
        "11e+22" to NUMERIC_LITERAL,
        "-" to MINUS,
        "11.22e-33" to NUMERIC_LITERAL)
  }

  fun testStrings() {
    assertTokenTypes(
        """select "",'foo''bar','foo"bar'""",
        "select" to SELECT,
        SPACE,
        "\"\"" to STRING_LITERAL,
        "," to COMMA,
        "'foo''bar'" to STRING_LITERAL,
        "," to COMMA,
        "'foo\"bar'" to STRING_LITERAL)

    assertTokenTypes(
        """CREATE TABLE "TABLE"("#!@""'☺\", "");""",
        "CREATE" to CREATE,
        SPACE,
        "TABLE" to TABLE,
        SPACE,
        """"TABLE"""" to STRING_LITERAL,
        "(" to LPAREN,
        """"#!@""'☺\"""" to STRING_LITERAL,
        "," to COMMA,
        SPACE,
        "\"\"" to STRING_LITERAL,
        ")" to RPAREN,
        ";" to SEMICOLON)
  }
}
