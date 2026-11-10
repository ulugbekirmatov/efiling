package com.irs.mef.w2sheet;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Greedy word wrap of mixed-font runs into lines no wider than a column. */
final class TextWrap {

    record Word(String text, PDFont font) {}

    private TextWrap() {}

    static List<Word> words(String text, PDFont font) {
        return Arrays.stream(text.trim().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(word -> new Word(word, font))
                .toList();
    }

    static List<List<Word>> wrap(List<Word> words, float size, float width) {
        List<List<Word>> lines = new ArrayList<>();
        List<Word> line = new ArrayList<>();
        float used = 0f;
        for (Word word : words) {
            float wordWidth = Pen.width(word.font(), size, word.text());
            if (wordWidth > width) {
                throw new W2SheetException("'" + word.text() + "' is wider than its " + width + "pt column");
            }
            float needed = line.isEmpty() ? wordWidth : used + space(word.font(), size) + wordWidth;
            if (needed > width) {
                lines.add(line);
                line = new ArrayList<>();
                needed = wordWidth;
            }
            line.add(word);
            used = needed;
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    static void draw(Pen pen, List<Word> line, float size, float x, float baseline) {
        float cursor = x;
        for (Word word : line) {
            pen.text(word.font(), size, cursor, baseline, word.text());
            cursor += Pen.width(word.font(), size, word.text()) + space(word.font(), size);
        }
    }

    private static float space(PDFont font, float size) {
        return Pen.width(font, size, " ");
    }
}
