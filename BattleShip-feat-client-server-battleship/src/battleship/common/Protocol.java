package battleship.common;

import java.util.*;

/** Small JSON-lines protocol codec; supports objects, arrays, strings, numbers, booleans and null. */
public final class Protocol {
    private Protocol() {}
    public static Map<String,Object> message(String type, String requestId, Map<String,Object> payload) {
        Map<String,Object> m = new LinkedHashMap<>(); m.put("type", type);
        if (requestId != null) m.put("requestId", requestId);
        m.put("payload", payload == null ? Map.of() : payload); return m;
    }
    public static String encode(Object value) { StringBuilder b = new StringBuilder(); write(value,b); return b.toString(); }
    @SuppressWarnings("unchecked") public static Map<String,Object> decode(String s) {
        Object v = new Parser(s).parse();
        if (!(v instanceof Map)) throw new IllegalArgumentException("Message must be a JSON object");
        return (Map<String,Object>)v;
    }
    @SuppressWarnings("unchecked") private static void write(Object v, StringBuilder b) {
        if (v == null) b.append("null");
        else if (v instanceof String) { b.append('"'); for(char c:((String)v).toCharArray()) switch(c) {
            case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break;
            case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break; default: if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);
        } b.append('"'); }
        else if (v instanceof Number || v instanceof Boolean) b.append(v);
        else if (v instanceof Map) { b.append('{'); boolean first=true; for(var e:((Map<String,Object>)v).entrySet()){if(!first)b.append(',');first=false;write(e.getKey(),b);b.append(':');write(e.getValue(),b);} b.append('}'); }
        else if (v instanceof Iterable) { b.append('[');boolean first=true;for(Object x:(Iterable<?>)v){if(!first)b.append(',');first=false;write(x,b);}b.append(']'); }
        else write(v.toString(),b);
    }
    private static final class Parser {
        final String s; int i; Parser(String s){this.s=s;}
        Object parse(){Object v=value(); ws(); if(i!=s.length())fail(); return v;}
        Object value(){ws();if(i>=s.length())return fail();char c=s.charAt(i);
            if(c=='{')return object();if(c=='[')return array();if(c=='"')return string();
            if(s.startsWith("true",i)){i+=4;return true;}if(s.startsWith("false",i)){i+=5;return false;}if(s.startsWith("null",i)){i+=4;return null;}return number();}
        Map<String,Object> object(){i++;Map<String,Object> m=new LinkedHashMap<>();ws();if(take('}'))return m;do{ws();String k=string();ws();need(':');m.put(k,value());ws();if(take('}'))return m;need(',');}while(true);}
        List<Object> array(){i++;List<Object>a=new ArrayList<>();ws();if(take(']'))return a;do{a.add(value());ws();if(take(']'))return a;need(',');}while(true);}
        String string(){need('"');StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c=='\\'){if(i>=s.length())fail();char e=s.charAt(i++);switch(e){case '"':case '\\':case '/':b.append(e);break;case 'b':b.append('\b');break;case 'f':b.append('\f');break;case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;case 'u':if(i+4>s.length())fail();b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;break;default:fail();}}else b.append(c);}return fail();}
        Number number(){int start=i;if(take('-')){}while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;if(take('.'))while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;if(i<s.length()&&(s.charAt(i)=='e'||s.charAt(i)=='E')){i++;if(i<s.length()&&(s.charAt(i)=='+'||s.charAt(i)=='-'))i++;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;}try{String n=s.substring(start,i);return n.contains(".")||n.contains("e")||n.contains("E")?Double.parseDouble(n):Long.parseLong(n);}catch(Exception e){return fail();}}
        void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}boolean take(char c){if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}void need(char c){if(!take(c))fail();}
        <T>T fail(){throw new IllegalArgumentException("Invalid JSON at offset "+i);}
    }
}
