/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.commons.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DV033G project: targeted boundary tests for {@link Lexer}, written to kill mutants that survived the PIT baseline
 * (tag pit-baseline, 208/220 killed). Each nested class names the Lexer line and the mutant it targets.
 */
class LexerBoundaryTest {

    @SuppressWarnings("resource")
    private static Lexer createLexer(final String input, final CSVFormat format) {
        return new Lexer(format, new ExtendedBufferedReader(new StringReader(input)));
    }

    /**
     * Lexer line 219, {@code isEscapeDelimiter()}: 3 surviving mutants (2x "integer multiplication replaced with
     * division", 1x "negated conditional").
     * <p>
     * Why they survived: the existing test uses delimiter {@code "[|]"}. If the fast path wrongly returns false, the
     * fallback {@code readEscape()} unescapes {@code ![ !| !]} one character at a time and produces the same text, so
     * the mutants are invisible. The fallback only differs when a delimiter character is also an escape-sequence
     * letter: {@code !n} means "line feed" in {@code readEscape()}, but inside an escaped delimiter it must stay a
     * literal {@code n}.
     * </p>
     */
    @Nested
    @DisplayName("Escaped multi-character delimiter that contains an escape-sequence letter")
    class EscapedDelimiterWithEscapeLetter {

        private final CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter("[n]").setEscape('!').get();

        @Test
        @DisplayName("Lexer: '![!n!]' is one escaped delimiter and stays the literal text '[n]'")
        void lexerKeepsLiteralN() throws IOException {
            try (Lexer lexer = createLexer("a![!n!]b[n]c", format)) {
                final Token first = lexer.nextToken(new Token());
                assertEquals(Token.Type.TOKEN, first.type);
                assertEquals("a[n]b", first.content.toString());
                final Token second = lexer.nextToken(new Token());
                assertEquals(Token.Type.EOF, second.type);
                assertEquals("c", second.content.toString());
            }
        }

        @Test
        @DisplayName("Parser: record keeps '[n]' in the field and splits on the unescaped delimiter")
        void parserKeepsLiteralN() throws IOException {
            try (CSVParser parser = CSVParser.parse("a![!n!]b[n]c", format)) {
                final List<CSVRecord> records = parser.getRecords();
                assertEquals(1, records.size());
                assertEquals("a[n]b", records.get(0).get(0));
                assertEquals("c", records.get(0).get(1));
            }
        }
    }

    /**
     * Lexer line 482, {@code readEndOfLine()}: surviving "negated conditional" on {@code if (firstEol == null)} in the
     * CRLF branch.
     * <p>
     * Why it survived: the existing test {@code testFirstEndOfLineCrLf} uses CRLF on every line and only checks
     * {@code getFirstEndOfLine()} after the whole input is parsed. The mutant overwrites the value on every later
     * CRLF, which ends up as the same final value. The Javadoc says "the <em>first</em> end-of-line string
     * encountered", so mixed line endings are the boundary that exposes it.
     * </p>
     */
    @Nested
    @DisplayName("getFirstEndOfLine() with mixed and single line endings")
    class FirstEndOfLine {

        @Test
        @DisplayName("LF first, CRLF later: first end-of-line stays LF")
        void lfThenCrLfKeepsLf() throws IOException {
            try (CSVParser parser = CSVParser.parse("a\nb\r\nc", CSVFormat.DEFAULT)) {
                assertEquals(3, parser.getRecords().size());
                assertEquals("\n", parser.getFirstEndOfLine());
            }
        }

        @Test
        @DisplayName("CR first, CRLF later: first end-of-line stays CR")
        void crThenCrLfKeepsCr() throws IOException {
            try (CSVParser parser = CSVParser.parse("a\rb\r\nc", CSVFormat.DEFAULT)) {
                assertEquals(3, parser.getRecords().size());
                assertEquals("\r", parser.getFirstEndOfLine());
            }
        }

        @Test
        @DisplayName("A single CRLF is reported as CRLF")
        void singleCrLf() throws IOException {
            try (CSVParser parser = CSVParser.parse("a\r\nb", CSVFormat.DEFAULT)) {
                assertEquals(2, parser.getRecords().size());
                assertEquals("\r\n", parser.getFirstEndOfLine());
            }
        }
    }

    /**
     * Lexer line 289, {@code nextToken()}: surviving "replaced return value with null" in the branch where a comment
     * marker is the last character of the input ({@code readLine()} returns null).
     * <p>
     * Why it survived: {@code CSVParser.nextRecord()} ignores the return value of {@code nextToken()} and reads the
     * token it passed in. Calling the Lexer directly, as {@code LexerTest} does, checks the method's own contract.
     * </p>
     */
    @Nested
    @DisplayName("Comment marker as the very last character")
    class CommentMarkerAtEndOfInput {

        private final CSVFormat format = CSVFormat.DEFAULT.builder().setCommentMarker('#').get();

        @Test
        @DisplayName("Lone '#' returns an EOF token, not null")
        void loneCommentMarker() throws IOException {
            try (Lexer lexer = createLexer("#", format)) {
                final Token token = lexer.nextToken(new Token());
                assertNotNull(token);
                assertEquals(Token.Type.EOF, token.type);
            }
        }

        @Test
        @DisplayName("Parser: a record followed by a lone '#' gives one record and no trailer comment")
        void recordThenLoneCommentMarker() throws IOException {
            try (CSVParser parser = CSVParser.parse("a,b\n#", format)) {
                final List<CSVRecord> records = parser.getRecords();
                assertEquals(1, records.size());
                assertEquals("b", records.get(0).get(1));
            }
        }
    }
}
