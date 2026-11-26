package common;

// ROOM_LIST 항목  ex) 1:치킨방:3/5:OPEN
public class RoomInfo {
    public final int id;
    public final String name;
    public final int users;
    public final int max;
    public final boolean closed;

    public RoomInfo(int id, String name, int users, int max, boolean closed) {
        this.id = id;
        this.name = name;
        this.users = users;
        this.max = max;
        this.closed = closed;
    }

    @Override
    public String toString() {
        return id + ":" + name + " (" + users + "/" + max + (closed ? ", CLOSED)" : ", OPEN)");
    }
}
