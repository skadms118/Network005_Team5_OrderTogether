package client;

/*
 * 메뉴판의 메뉴 한 개 (메뉴 번호, 이름, 가격).
 * 서버가 보내 준 MENU 메시지를 읽어서 만든다.
 *   예) "1:후라이드:18000"  ->  id=1, name="후라이드", price=18000
 */
class MenuItem {

    int id;         // 메뉴 번호
    String name;    // 메뉴 이름
    int price;      // 가격 (원)

    MenuItem(int id, String name, int price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }
}
