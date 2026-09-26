package dev.tirador.aoe2radar.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser JSON mínimo (suficiente para estas APIs).
 * <p>Tolera NaN, Infinity y -Infinity sueltos (no son JSON válido, pero pandas los escribe en sfr-data cuando un dato
 * falta: la civ de una partida de muestra_ayer, p. ej.): se leen como {@link #NO_NUMERO}, el valor ausente que ya daba
 * PerfilesSfr. Antes, un solo NaN tumbaba el archivo entero (la muestra nocturna dejaba de cargarse).
 */
public final class Json {
    /** Lo que se lee en lugar de NaN/Infinity/-Infinity: texto vacío, que los lectores ya tratan como «sin dato». */
    public static final String NO_NUMERO = "";

    private final String s; private int i;
    private Json(String s) { this.s = s; }

    public static Object parse(String s) {
        Json j = new Json(s);
        j.ws();
        Object v = j.value();
        return v;
    }

    Object value() {
        ws();
        char c = peek();
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> { expect("true");  yield Boolean.TRUE; }
            case 'f' -> { expect("false"); yield Boolean.FALSE; }
            case 'n' -> { expect("null");  yield null; }
            case 'N' -> { expect("NaN");      yield NO_NUMERO; }
            case 'I' -> { expect("Infinity"); yield NO_NUMERO; }
            default  -> number();
        };
    }

    Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            if (s.charAt(i++) != ':') throw err("':' esperado");
            m.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("',' o '}' esperado");
        }
    }

    List<Object> array() {
        List<Object> a = new ArrayList<>();
        i++; ws();
        if (peek() == ']') { i++; return a; }
        while (true) {
            a.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return a;
            if (c != ',') throw err("',' o ']' esperado");
        }
    }

    String string() {
        if (s.charAt(i) != '"') throw err("'\"' esperado");
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case '"' -> b.append('"');
                    case '\\' -> b.append('\\');
                    case '/' -> b.append('/');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> throw err("escape inválido");
                }
            } else b.append(c);
        }
    }

    Object number() {
        if (s.startsWith("-Infinity", i)) { i += "-Infinity".length(); return NO_NUMERO; }
        int start = i;
        while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(start, i);
        if (n.isEmpty()) throw err("valor inesperado");
        return Double.parseDouble(n);
    }

    void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    char peek() { return s.charAt(i); }
    void expect(String w) {
        if (!s.startsWith(w, i)) throw err("'" + w + "' esperado");
        i += w.length();
    }
    RuntimeException err(String m) { return new RuntimeException("JSON: " + m + " (pos " + i + ")"); }

    // ----- Utilidades JSON ---------------------------------------------------
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : Map.of(); }
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return o instanceof List ? (List<Object>) o : List.of(); }

    public static Object val(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.containsKey(k)) return m.get(k);
        return null;
    }

    public static Object firstNonNull(Object... xs) { for (Object x : xs) if (x != null) return x; return null; }

    public static String str(Object o) { return o == null ? null : String.valueOf(o); }

    public static long lng(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return -1; }
    }

    /**
     * Fecha de la API: número (segundos o, si pasa de 1e11, milisegundos) o texto ISO. Lo ilegible queda null, también
     * un número fuera del rango de Instant (p. ej. -1e30): así una fila absurda no tumba la página entera.
     */
    public static Instant when(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) {
            long v = n.longValue();
            try { return v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v); }
            catch (java.time.DateTimeException e) { return null; }
        }
        String s = String.valueOf(o);
        try { return Instant.parse(s); }
        catch (Exception e) {
            try { return OffsetDateTime.parse(s).toInstant(); }
            catch (Exception e2) { return null; }
        }
    }

    public static Map<String, Object> leerGzJson(byte[] raw) throws Exception {
        try (java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) Json.parse(new String(gz.readAllBytes(), StandardCharsets.UTF_8));
            return m;
        }
    }
}
