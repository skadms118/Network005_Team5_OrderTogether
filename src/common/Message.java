package common;

import java.util.Arrays;

// "JOIN|1|영희" -> command=JOIN, fields=[1, 영희]
public class Message {
    public final String command;
    public final String[] fields;

    public Message(String command, String[] fields) {
        this.command = command;
        this.fields = fields;
    }

    // 없는 칸이면 "" 반환
    public String field(int i) {
        return i < fields.length ? fields[i] : "";
    }

    public int size() {
        return fields.length;
    }

    @Override
    public String toString() {
        return command + " " + Arrays.toString(fields);
    }
}
