package ken.mizoguch.webviewer.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.math.BigDecimal;

/**
 * Strict JSON parsing, validation, and formatting helpers shared across the {@code llm} package.
 *
 * @author mizoguch-ken
 */
final class JsonUtil {

    static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private JsonUtil() {
    }

    static JsonObject parseObject(String json, String context) {
        if ((json == null) || json.trim().isEmpty()) {
            throw new IllegalArgumentException(context + " must be a non-empty JSON object");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if ((parsed == null) || !parsed.isJsonObject()) {
                throw new IllegalArgumentException(context + " must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException(context + " is not valid JSON: " + ex.getMessage(),
                    ex);
        }
    }

    static JsonObject tryParseObject(String json) {
        if ((json == null) || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return ((parsed != null) && parsed.isJsonObject()) ? parsed.getAsJsonObject() : null;
        } catch (JsonSyntaxException ex) {
            return null;
        }
    }

    static JsonArray parseArray(String json, String context) {
        if ((json == null) || json.trim().isEmpty()) {
            throw new IllegalArgumentException(context + " must be a non-empty JSON array");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if ((parsed == null) || !parsed.isJsonArray()) {
                throw new IllegalArgumentException(context + " must be a JSON array");
            }
            return parsed.getAsJsonArray();
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException(context + " is not valid JSON: " + ex.getMessage(),
                    ex);
        }
    }

    static String optString(JsonObject obj, String key) {
        JsonElement el = (obj == null) ? null : obj.get(key);
        if ((el == null) || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("\"" + key + "\" must be a string");
        }
        return el.getAsString();
    }

    static Integer optInt(JsonObject obj, String key) {
        JsonElement el = (obj == null) ? null : obj.get(key);
        if ((el == null) || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("\"" + key + "\" must be an integer");
        }
        try {
            double val = el.getAsDouble();
            if (!Double.isFinite(val) || (val != Math.rint(val))
                    || (val < Integer.MIN_VALUE) || (val > Integer.MAX_VALUE)) {
                throw new IllegalArgumentException("\"" + key + "\" must be a 32-bit integer");
            }
            return Integer.valueOf((int) val);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("\"" + key + "\" must be an integer", ex);
        }
    }

    static Long optLong(JsonObject obj, String key) {
        JsonElement el = (obj == null) ? null : obj.get(key);
        if ((el == null) || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("\"" + key + "\" must be an integer");
        }
        try {
            BigDecimal bd = el.getAsBigDecimal();
            return Long.valueOf(bd.longValueExact());
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("\"" + key + "\" must be a 64-bit integer", ex);
        }
    }

    static Double optDouble(JsonObject obj, String key) {
        JsonElement el = (obj == null) ? null : obj.get(key);
        if ((el == null) || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("\"" + key + "\" must be a finite number");
        }
        try {
            double val = el.getAsDouble();
            if (!Double.isFinite(val)) {
                throw new IllegalArgumentException("\"" + key + "\" must be a finite number");
            }
            return Double.valueOf(val);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("\"" + key + "\" must be a finite number", ex);
        }
    }

    static Boolean optBoolean(JsonObject obj, String key) {
        JsonElement el = (obj == null) ? null : obj.get(key);
        if ((el == null) || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("\"" + key + "\" must be a boolean");
        }
        return Boolean.valueOf(el.getAsBoolean());
    }

    static JsonObject optObject(JsonObject obj, String key) {
        JsonElement el = (obj == null) ? null : obj.get(key);
        if ((el == null) || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonObject()) {
            throw new IllegalArgumentException("\"" + key + "\" must be a JSON object");
        }
        return el.getAsJsonObject();
    }

    static JsonArray optArray(JsonObject obj, String key) {
        JsonElement el = (obj == null) ? null : obj.get(key);
        if ((el == null) || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonArray()) {
            throw new IllegalArgumentException("\"" + key + "\" must be a JSON array");
        }
        return el.getAsJsonArray();
    }

    static int[] parseIntArray(String json) {
        JsonArray arr = parseArray(json, "tokens");
        int[] ids = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if ((el == null) || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Token at index " + i + " is not an integer");
            }
            double d;
            try {
                d = el.getAsDouble();
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Token at index " + i + " is not an integer", ex);
            }
            if (!Double.isFinite(d) || (d != Math.rint(d))
                    || (d < Integer.MIN_VALUE) || (d > Integer.MAX_VALUE)) {
                throw new IllegalArgumentException("Token at index " + i + " is not an integer");
            }
            ids[i] = (int) d;
        }
        return ids;
    }

    static float[] parseFloatVector(String json, String context) {
        JsonArray arr = parseArray(json, context);
        return parseFloatVector(arr, context);
    }

    static float[] parseFloatVector(JsonElement el, String context) {
        if ((el == null) || !el.isJsonArray()) {
            throw new IllegalArgumentException(context + " must be a JSON array of numbers");
        }
        JsonArray arr = el.getAsJsonArray();
        if (arr.isEmpty()) {
            throw new IllegalArgumentException(context + " must not be empty");
        }
        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if ((item == null) || !item.isJsonPrimitive()
                    || !item.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(
                        context + " element at index " + i + " is not a finite number");
            }
            float val;
            try {
                val = (float) item.getAsDouble();
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        context + " element at index " + i + " is not a finite number", ex);
            }
            if (!Float.isFinite(val)) {
                throw new IllegalArgumentException(
                        context + " element at index " + i + " is not finite");
            }
            vec[i] = val;
        }
        return vec;
    }

    static JsonElement finiteNumber(double value) {
        return Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    }

    static String scriptNumber(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
    }

    static double roundMillis(double seconds) {
        if (!Double.isFinite(seconds)) {
            return seconds;
        }
        return Math.round(seconds * 1000d) / 1000d;
    }

    static String truncate(String text, int maxLength) {
        if ((text == null) || (maxLength < 0) || (text.length() <= maxLength)) {
            return text;
        }
        if (maxLength == 0) {
            return "...";
        }
        int cut = maxLength;
        if (Character.isHighSurrogate(text.charAt(cut - 1))) {
            cut--;
        }
        return text.substring(0, cut) + "...";
    }
}
