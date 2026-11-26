package common;

// MENU 항목  ex) 1:후라이드:18000
public class MenuItem {
    public final int id;
    public final String name;
    public final int price;

    public MenuItem(int id, String name, int price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    @Override
    public String toString() {
        return id + "." + name + " " + price;
    }
}
