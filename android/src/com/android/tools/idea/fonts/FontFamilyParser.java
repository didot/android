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
package com.android.tools.idea.fonts;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.fonts.FontDetail.DEFAULT_WEIGHT;
import static com.android.tools.idea.fonts.FontDetail.DEFAULT_WIDTH;

/**
 * Parse a font xml file.
 * Each file is either a specification of a downloadable font with a font query,
 * or a specification of a family with references to individual fonts.
 */
class FontFamilyParser {

  @NotNull
  static QueryParser.ParseResult parseFontFamily(@NotNull File xmlFile) {
    try {
      return parseFontReference(xmlFile);
    }
    catch (SAXException | ParserConfigurationException | IOException ex) {
      String message = "Could not parse font xml file " + xmlFile;
      Logger.getInstance(FontFamilyParser.class).error(message, ex);
      return new QueryParser.ParseErrorResult(message);
    }
  }

  private static QueryParser.ParseResult parseFontReference(@NotNull File xmlFile)
    throws SAXException, ParserConfigurationException, IOException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    SAXParser parser = factory.newSAXParser();
    FontFamilyHandler handler = new FontFamilyHandler(xmlFile);
    parser.parse(xmlFile, handler);
    return handler.getResult();
  }

  private static class FontFamilyHandler extends DefaultHandler {
    private static final String FONT_FAMILY = "font-family";
    private static final String FONT = "font";
    private static final String ATTR_AUTHORITY = "android:fontProviderAuthority";
    private static final String ATTR_QUERY = "android:fontProviderQuery";
    private static final String ATTR_FONT = "android:font";
    private static final String ATTR_FONT_WEIGHT = "android:fontWeight";
    private static final String ATTR_FONT_WIDTH = "android:fontWidth";
    private static final String ATTR_FONT_STYLE = "android:fontStyle";

    private final File myFile;
    private QueryParser.ParseResult myResult;

    private FontFamilyHandler(@NotNull File file) {
      myFile = file;
    }

    @NotNull
    private QueryParser.ParseResult getResult() {
      if (myResult == null) {
        myResult = new QueryParser.ParseErrorResult("The font file is empty");
      }
      return myResult;
    }

    @Override
    public void startElement(@NotNull String uri, @NotNull String localName, @NotNull String name, @NotNull Attributes attributes)
      throws SAXException {
      switch (name) {
        case FONT_FAMILY:
          myResult = parseQuery(attributes.getValue(ATTR_AUTHORITY), attributes.getValue(ATTR_QUERY));
          break;
        case FONT:
          String fontName = attributes.getValue(ATTR_FONT);
          int weight = parseInt(attributes.getValue(ATTR_FONT_WEIGHT), DEFAULT_WEIGHT);
          int width = parseInt(attributes.getValue(ATTR_FONT_WIDTH), DEFAULT_WIDTH);
          boolean italics = parseFontStyle(attributes.getValue(ATTR_FONT_STYLE));
          myResult = addFont(fontName, weight, width, italics);
          break;
        default:
          Logger.getInstance(FontFamilyParser.class).warn("Unrecognized tag: " + name + " in file: " + myFile);
          break;
      }
    }

    /**
     * Parse the downloadable font query if present
     *
     * The XML file may be either a downloadable font with required attributes: font authority and a query attribute,
     * or the file may be a font family definition which combines several font tags.
     */
    @Nullable
    private QueryParser.ParseResult parseQuery(@Nullable String authority, @Nullable String query) {
      // If there already is an error condition stop
      if (myResult instanceof QueryParser.ParseErrorResult) {
        return myResult;
      }
      // If neither an authority or a query is defined then this XML file must be a font family definition.
      // Simply return the existing result (which may be null).
      if (authority == null && query == null) {
        return myResult;
      }
      if (myResult != null) {
        return new QueryParser.ParseErrorResult("<" + FONT_FAMILY + "> must be the root element");
      }
      if (authority == null) {
        return new QueryParser.ParseErrorResult("The <" + FONT_FAMILY + "> tag must contain an " + ATTR_AUTHORITY + " attribute");
      }
      if (query == null) {
        return new QueryParser.ParseErrorResult("The <" + FONT_FAMILY + "> tag must contain a " + ATTR_QUERY + " attribute");
      }
      return QueryParser.parseDownloadableFont(authority, query);
    }

    private QueryParser.ParseResult addFont(@Nullable String fontName, int weight, int width, boolean italics) {
      if (myResult instanceof QueryParser.ParseErrorResult) {
        return myResult;
      }
      if (myResult != null && !(myResult instanceof CompoundFontResult)) {
        return new QueryParser.ParseErrorResult("<" + FONT + "> is not allowed in a downloadable font definition");
      }
      if (fontName == null) {
        return new QueryParser.ParseErrorResult("The <" + FONT + "> tag must contain a " + ATTR_FONT + " attribute");
      }
      CompoundFontResult result = (CompoundFontResult)myResult;
      if (result == null) {
        result = new CompoundFontResult();
      }
      result.addFont(fontName, weight, width, italics);
      return result;
    }
  }

  static class CompoundFontResult extends QueryParser.ParseResult {
    private Map<String, FontDetail.Builder> myFonts;

    CompoundFontResult() {
      myFonts = new HashMap<>();
    }

    @NotNull
    Map<String, FontDetail.Builder> getFonts() {
      return myFonts;
    }

    private void addFont(@NotNull String fontName, int weight, int width, boolean italics) {
      myFonts.put(fontName, new FontDetail.Builder(weight, width, italics, "", null));
    }
  }

  static int parseInt(@Nullable String intAsString, int defaultValue) {
    if (intAsString == null) {
      return defaultValue;
    }
    try {
      return Math.round(Float.parseFloat(intAsString));
    }
    catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  static boolean parseItalics(@Nullable String italics) {
    return italics != null && italics.startsWith("1");
  }

  static boolean parseFontStyle(@Nullable String fontStyle) {
    return fontStyle != null && fontStyle.startsWith("italic");
  }
}
