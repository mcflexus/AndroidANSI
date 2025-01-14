package com.fox2code.androidansi;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

public final class AnsiParser {
    private static final String TAG = "AnsiParser";
    // ANSI Has 2 escape sequences, let support both
    private static final String ESCAPE1 = "^[";
    private static final String ESCAPE2 = "[";

    // Any disabled attributes are unmodified.
    // Disable colors, implies FLAG_PARSE_DISABLE_EXTRAS_COLORS
    public static final int FLAG_PARSE_DISABLE_COLORS = 0x0001;
    // Disable attributes like italic, bold, underline and crossed out text
    // implies FLAG_PARSE_DISABLE_SUBSCRIPT
    public static final int FLAG_PARSE_DISABLE_ATTRIBUTES    = 0x0002;
    // Disable extra color customization other than foreground or background
    // Useful to have consistent display across Android versions
    public static final int FLAG_PARSE_DISABLE_EXTRAS_COLORS = 0x0004;
    // Disable subscript and superscript text
    public static final int FLAG_PARSE_DISABLE_SUBSCRIPT   = 0x0008;
    // Disable all attributes changes
    public static final int FLAGS_DISABLE_ALL =
            FLAG_PARSE_DISABLE_COLORS | FLAG_PARSE_DISABLE_ATTRIBUTES;

    /**
     * Replace escape sequence using a control character by a normal escape sequence
     * This allow to use {@link String#trim()} without the risk for the escape sequence
     * to be elso trimmed
     */
    public static String patchEscapeSequence(String string) {
        return string.replace(ESCAPE2, ESCAPE1);
    }

    public static Spannable parseAsSpannable(@NonNull String text) {
        return parseAsSpannable(text, null);
    }

    public static Spannable parseAsSpannable(
            @NonNull String text, @Nullable AnsiContext ansiContext) {
        return parseAsSpannable(text, ansiContext, 0);
    }

    public static Spannable parseAsSpannable(
            @NonNull String text, int parseFlags) {
        return parseAsSpannable(text, null, parseFlags);
    }

    public static Spannable parseAsSpannable(
            @NonNull String text, @Nullable AnsiContext ansiContext, int parseFlags) {
        if (text.length() == 0) return new SpannableString(text);
        ColorTransformer transformer = AnsiConstants.NO_OP_TRANSFORMER;
        ansiContext = (ansiContext == null ? AnsiContext.DARK : ansiContext).asMutable();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        int index = 0, indexEx = 0;
        while (true) {
            int index1 = text.indexOf(ESCAPE1, indexEx);
            int index2 = text.indexOf(ESCAPE2, indexEx);
            if (index1 == -1 && index2 == -1) break;
            int nextIndex = index2 == -1 || (index1 != -1 &&
                    index1 < index2) ? index1 + 3 : index2 + 2;
            int i = text.indexOf('m', nextIndex);
            if (i == -1 || i > (nextIndex + 17)) {
                indexEx = nextIndex;
                continue;
            }
            // ITU use ":" instead of ";", lets support it.
            String code = text.substring(nextIndex, i).replace(':', ';');
            if (code.length() != 0 && (code.charAt(0) < '0' || code.charAt(0) > '9')) {
                indexEx = nextIndex;
                continue;
            }
            int currentEnd = index2 == -1 || (index1 != -1
                    && index1 < index2) ? index1 : index2;
            if (currentEnd != index) {
                int nStart = spannableStringBuilder.length(),
                        nEnd = nStart + (currentEnd - index);
                spannableStringBuilder.append(text, index, currentEnd);
                spannableStringBuilder.setSpan(ansiContext.toAnsiTextSpan(), nStart, nEnd, 0);
            }
            index = indexEx = i + 1;
            try {
                parseTokens(code.split(";"), ansiContext, parseFlags);
            } catch (RuntimeException e) {
                // This can be normal if an invalid number is supplied
                Log.d(TAG, "Failed to parse ansi code: " + ESCAPE1 + code + "m", e);
            }
        }
        int currentEnd = text.length();
        if (currentEnd != index) {
            int nStart = spannableStringBuilder.length(),
                    nEnd = nStart + (currentEnd - index);
            spannableStringBuilder.append(text, index, currentEnd);
            spannableStringBuilder.setSpan(ansiContext.toAnsiTextSpan(), nStart, nEnd, 0);
        }
        return spannableStringBuilder;
    }

    public static void parseTokens(String[] tokens, AnsiContext ansiContext,int parseFlags) {
        int tokenIndex = 0;
        while (tokenIndex < tokens.length) {
            String token = tokens[tokenIndex];
            int ansiCode = token.isEmpty() ? 0 : Integer.parseInt(token);
            tokenIndex++;
            if ((parseFlags & FLAG_PARSE_DISABLE_ATTRIBUTES) == 0) {
                switch (ansiCode) {
                    case 0:
                        ansiContext.style = 0;
                    default:
                        break;
                    case 1:
                        ansiContext.style &= ~AnsiConstants.FLAG_STYLE_DIM;
                        ansiContext.style |= AnsiConstants.FLAG_STYLE_BOLD;
                        break;
                    case 2:
                        ansiContext.style &= ~AnsiConstants.FLAG_STYLE_BOLD;
                        ansiContext.style |= AnsiConstants.FLAG_STYLE_DIM;
                        break;
                    case 3:
                        ansiContext.style |= AnsiConstants.FLAG_STYLE_ITALIC;
                        break;
                    case 4:
                        ansiContext.style |= AnsiConstants.FLAG_STYLE_UNDERLINE;
                        break;
                    case 9:
                        ansiContext.style |= AnsiConstants.FLAG_STYLE_STRIKE;
                        break;
                    case 21:
                        ansiContext.style &= ~AnsiConstants.FLAG_STYLE_BOLD;
                        break;
                    case 22:
                        ansiContext.style &= ~(AnsiConstants.FLAG_STYLE_BOLD |
                                AnsiConstants.FLAG_STYLE_DIM);
                        break;
                    case 23:
                        ansiContext.style &= ~AnsiConstants.FLAG_STYLE_ITALIC;
                        break;
                    case 24:
                        ansiContext.style &= ~AnsiConstants.FLAG_STYLE_UNDERLINE;
                        break;
                    case 29:
                        ansiContext.style &= ~AnsiConstants.FLAG_STYLE_STRIKE;
                        break;
                    case 73:
                        if ((parseFlags & FLAG_PARSE_DISABLE_SUBSCRIPT) == 0) {
                            ansiContext.style &= ~AnsiConstants.FLAG_STYLE_SUBSCRIPT;
                            ansiContext.style |= AnsiConstants.FLAG_STYLE_SUPERSCRIPT;
                        }
                        break;
                    case 74:
                        if ((parseFlags & FLAG_PARSE_DISABLE_SUBSCRIPT) == 0) {
                            ansiContext.style &= ~AnsiConstants.FLAG_STYLE_SUPERSCRIPT;
                            ansiContext.style |= AnsiConstants.FLAG_STYLE_SUBSCRIPT;
                        }
                        break;
                    case 75:
                        if ((parseFlags & FLAG_PARSE_DISABLE_SUBSCRIPT) == 0) {
                            ansiContext.style &= ~(AnsiConstants.FLAG_STYLE_SUBSCRIPT |
                                    AnsiConstants.FLAG_STYLE_SUPERSCRIPT);
                        }
                }
            }
            if ((parseFlags & FLAG_PARSE_DISABLE_COLORS) == 0) {
                boolean bg = false, ul = false;
                switch (ansiCode) {
                    default:
                        if ((ansiCode >= 30 && ansiCode < 50) ||
                                (ansiCode >= 90 && ansiCode < 110)) {
                            bg = ansiCode >= 40 &&
                                    ansiCode < 50 || ansiCode >= 100;
                            int color = AnsiConstants.colorForAnsiCode(ansiCode);
                            if (bg) {
                                ansiContext.background =
                                        ansiContext.transformer.transform(
                                                color, ColorTransformer.ROLE_BACKGROUND);
                            } else {
                                ansiContext.foreground =
                                        ansiContext.transformer.transform(
                                                color, ColorTransformer.ROLE_FOREGROUND);
                            }
                        }
                        break;
                    case 0:
                        ansiContext.foreground = ansiContext.defaultForeground;
                        ansiContext.background = ansiContext.defaultBackground;
                        if ((parseFlags & FLAG_PARSE_DISABLE_EXTRAS_COLORS) == 0)
                            ansiContext.underline = Color.TRANSPARENT;
                        break;
                    case 7:
                        int tmp = ansiContext.foreground;
                        ansiContext.foreground = ansiContext.background;
                        ansiContext.background = tmp;
                        break;
                    case 39:
                        ansiContext.foreground = ansiContext.defaultForeground;
                        break;
                    case 49:
                        ansiContext.background = ansiContext.defaultBackground;
                        break;
                    case 59:
                        if ((parseFlags & FLAG_PARSE_DISABLE_EXTRAS_COLORS) == 0)
                            ansiContext.underline = Color.TRANSPARENT;
                        break;
                    case 58:
                        if ((parseFlags & FLAG_PARSE_DISABLE_EXTRAS_COLORS) != 0)
                            continue;
                        ul = true;
                    case 48:
                        bg = true;
                    case 38: {
                        if (tokenIndex == tokens.length) {
                            Log.w(TAG, "Can't process true color: " + Arrays.toString(tokens));
                            continue;
                        }
                        int type = Integer.parseInt(tokens[tokenIndex]);
                        int color;
                        switch (type) {
                            case 2:
                                if (tokens.length < tokenIndex + 4) {
                                    Log.w(TAG, "Can't process 24-bit true color: " +
                                            Arrays.toString(tokens));
                                    tokenIndex = tokens.length; // Stop loop
                                    continue;
                                }
                                color = Color.rgb(Integer.parseInt(tokens[tokenIndex + 1]),
                                        Integer.parseInt(tokens[tokenIndex + 2]),
                                        Integer.parseInt(tokens[tokenIndex + 3]));
                                tokenIndex += 4;
                                break;
                            case 5:
                                if (tokens.length < tokenIndex + 2) {
                                    Log.w(TAG, "Can't process 8-bit true color: " +
                                            Arrays.toString(tokens));
                                    tokenIndex = tokens.length; // Stop loop
                                    continue;
                                }
                                color = Integer.parseInt(tokens[tokenIndex + 1]);
                                tokenIndex += 2;
                                if (color < 0 || color > 15) {
                                    Log.w(TAG, "Can't process 8-bit true color: " +
                                            Arrays.toString(tokens));
                                    continue;
                                }
                                // Transform 8-bit true color to ANSI color
                                color = AnsiConstants.colorForAnsiCode(
                                        color + (color < 8 ? 30 : 82));
                                break;
                            default:
                                Log.w(TAG, "Unknown true color type: " +
                                        Arrays.toString(tokens));
                                continue;
                        }
                        if (ul) {
                            ansiContext.underline =
                                    ansiContext.transformer.transform(
                                            color, ColorTransformer.ROLE_UNDERLINE);
                        } else if (bg) {
                            ansiContext.background =
                                    ansiContext.transformer.transform(
                                            color, ColorTransformer.ROLE_BACKGROUND);
                        } else {
                            ansiContext.foreground =
                                    ansiContext.transformer.transform(
                                            color, ColorTransformer.ROLE_FOREGROUND);
                        }
                    }
                }
            }
        }
    }

    public static void setAnsiText(@NonNull TextView text,@NonNull String ansiText) {
        text.setText(parseAsSpannable(ansiText));
    }

    public static void setAnsiText(@NonNull TextView text,@NonNull String ansiText, int parseFlags) {
        text.setText(parseAsSpannable(ansiText, parseFlags));
    }

    public static String trimEscapeSequences(String string) {
        return parseAsSpannable(string, FLAGS_DISABLE_ALL).toString();
    }

    private AnsiParser() {}
}
