package com.lenspreset.core.json;

public class JsonException extends Exception {
    public final int line;
    public final int column;

    public JsonException(String message, int line, int column) {
        super(message + " at line " + line + ", column " + column);
        this.line = line;
        this.column = column;
    }
}
