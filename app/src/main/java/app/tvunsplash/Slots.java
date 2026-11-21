package app.tvunsplash;

import java.time.LocalDateTime;
import java.util.List;

/**
 * When a new photo is due and what to search for. No Android types, so it is tested on the JVM (SlotsTest).
 *
 * 4 photos a day (the default) follow the light: morning 05-11, day 11-16, evening 16-19, night 19-05.
 * Any other number splits the day into that many equal parts from midnight (24 = every hour).
 */
final class Slots {
    static final int DEFAULT_PER_DAY = 4;
    static final int MAX_PER_DAY = 48;

    /** Built-in searches by time of day, used when no topics are set. */
    static final String[] MORNING = {"sunrise mountains", "misty morning landscape", "sunrise lake", "morning valley",
            "sunrise beach", "morning forest light"};
    static final String[] DAY = {"mountain lake", "alpine landscape", "coastline aerial", "national park landscape",
            "waterfall landscape", "desert landscape", "himalaya"};
    static final String[] EVENING = {"sunset mountains", "golden hour landscape", "sunset coast", "dusk lake",
            "sunset desert"};
    static final String[] NIGHT = {"milky way landscape", "night city skyline", "aurora borealis",
            "starry night mountains", "city lights night"};

    private Slots() { }

    /** 1 to MAX_PER_DAY; anything else is the default. */
    static int perDay(int n) {
        return n >= 1 && n <= MAX_PER_DAY ? n : DEFAULT_PER_DAY;
    }

    /** The light at this hour: morning, day, evening or night. */
    static String mood(int hour) {
        if (hour >= 5 && hour < 11) return "morning";
        if (hour >= 11 && hour < 16) return "day";
        if (hour >= 16 && hour < 19) return "evening";
        return "night";
    }

    /** Which part of the day this is, 0 .. perDay-1 (4 a day: 0 morning, 1 day, 2 evening, 3 night). */
    static int index(LocalDateTime now, int perDay) {
        perDay = perDay(perDay);
        if (perDay == DEFAULT_PER_DAY) {
            switch (mood(now.getHour())) {
                case "morning": return 0;
                case "day": return 1;
                case "evening": return 2;
                default: return 3;
            }
        }
        int minute = now.getHour() * 60 + now.getMinute();
        return Math.min(perDay - 1, minute / (1440 / perDay));
    }

    /**
     * The slot now, as "yyyy-mm-dd#..": a new photo is due whenever it changes. With 4 a day the night after midnight
     * still belongs to the previous evening's date, so it is one slot, not two.
     */
    static String id(LocalDateTime now, int perDay) {
        perDay = perDay(perDay);
        if (perDay == DEFAULT_PER_DAY) {
            String m = mood(now.getHour());
            boolean afterMidnight = m.equals("night") && now.getHour() < 5;
            return (afterMidnight ? now.toLocalDate().minusDays(1) : now.toLocalDate()) + "#" + m;
        }
        return now.toLocalDate() + "#" + perDay + "-" + index(now, perDay);
    }

    /**
     * What to search for: the owner's topics in turn when set (a different one for each slot of the year), else the
     * built-in searches for the light at this hour.
     */
    static String query(LocalDateTime now, int perDay, List<String> topics) {
        perDay = perDay(perDay);
        int turn = now.getDayOfYear() * perDay + index(now, perDay);
        if (topics != null && !topics.isEmpty()) return topics.get(Math.floorMod(turn, topics.size()));
        String[] list;
        switch (mood(now.getHour())) {
            case "morning": list = MORNING; break;
            case "day": list = DAY; break;
            case "evening": list = EVENING; break;
            default: list = NIGHT;
        }
        return list[Math.floorMod(now.getDayOfYear(), list.length)];
    }
}
