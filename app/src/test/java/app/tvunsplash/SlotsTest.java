package app.tvunsplash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SlotsTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 21, hour, minute);
    }

    @Test
    public void fourADayFollowsTheLight() {
        assertEquals("2026-11-21#morning", Slots.id(at(5, 0), 4));
        assertEquals("2026-11-21#day", Slots.id(at(11, 0), 4));
        assertEquals("2026-11-21#evening", Slots.id(at(16, 30), 4));
        assertEquals("2026-11-21#night", Slots.id(at(19, 0), 4));
    }

    @Test
    public void nightAfterMidnightIsTheSameSlotAsTheEveningBefore() {
        assertEquals("2026-11-20#night", Slots.id(at(2, 0), 4));
        assertEquals(Slots.id(LocalDateTime.of(2026, 11, 20, 23, 0), 4), Slots.id(at(2, 0), 4));
    }

    @Test
    public void otherCountsSplitTheDayEvenly() {
        assertEquals("2026-11-21#24-0", Slots.id(at(0, 59), 24));
        assertEquals("2026-11-21#24-23", Slots.id(at(23, 0), 24));
        assertEquals("2026-11-21#1-0", Slots.id(at(23, 59), 1));
        assertEquals("2026-11-21#3-1", Slots.id(at(8, 0), 3));
        assertNotEquals(Slots.id(at(7, 59), 3), Slots.id(at(8, 0), 3));
    }

    @Test
    public void everyCountGivesExactlyThatManySlotsADay() {
        for (int n = 1; n <= Slots.MAX_PER_DAY; n++) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int minute = 0; minute < 1440; minute++) ids.add(Slots.id(at(minute / 60, minute % 60), n));
            int expected = n == 4 ? 5 : n;   // 4 a day: the early-morning night belongs to the day before
            assertEquals("per day " + n, expected, ids.size());
        }
    }

    @Test
    public void outOfRangeCountsFallBackToFour() {
        assertEquals(4, Slots.perDay(0));
        assertEquals(4, Slots.perDay(49));
        assertEquals(12, Slots.perDay(12));
    }

    @Test
    public void ownTopicsAreTakenInTurn() {
        List<String> topics = Arrays.asList("architecture", "street", "wildlife");
        String a = Slots.query(at(1, 0), 24, topics), b = Slots.query(at(2, 0), 24, topics);
        assertTrue(topics.contains(a));
        assertNotEquals(a, b);
    }

    @Test
    public void noTopicsUsesTheBuiltInSearchesForTheLight() {
        assertTrue(Arrays.asList(Slots.MORNING).contains(Slots.query(at(7, 0), 4, Collections.emptyList())));
        assertTrue(Arrays.asList(Slots.NIGHT).contains(Slots.query(at(22, 0), 12, null)));
    }
}
