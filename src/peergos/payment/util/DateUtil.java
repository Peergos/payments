package peergos.payment.util;

import java.time.LocalTime;

public class DateUtil {
    public static LocalTime toTime(String hhcolonmm) {
        String colon = ":";
        int colonIndex = hhcolonmm.indexOf(colon);
        if(colonIndex == -1 || colonIndex == 0 || colonIndex == hhcolonmm.length() -1) {
            throw new IllegalArgumentException("Incorrect time format:" + hhcolonmm);
        }
        String[] parts = hhcolonmm.trim().split(":");
        String hour = parts[0];
        String minute = parts[1];
        return LocalTime.of(Integer.valueOf(hour), Integer.valueOf(minute));
    }
}
