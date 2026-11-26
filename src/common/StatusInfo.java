package common;

import java.util.LinkedHashMap;
import java.util.Map;

// STATUS / FINAL 내용
// byUser: 닉네임 -> (메뉴ID -> 수량), counts: 메뉴ID -> 총 수량, total: 총금액
public class StatusInfo {
    public final Map<String, Map<Integer, Integer>> byUser;
    public final Map<Integer, Integer> counts;
    public final int total;

    public StatusInfo(Map<String, Map<Integer, Integer>> byUser, Map<Integer, Integer> counts, int total) {
        this.byUser = byUser;
        this.counts = counts;
        this.total = total;
    }

    public static StatusInfo empty() {
        return new StatusInfo(new LinkedHashMap<>(), new LinkedHashMap<>(), 0);
    }

    @Override
    public String toString() {
        return byUser + " / " + counts + " / " + total;
    }
}
