package com.seatly.app;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatAllocationEngineTest {
    @Test
    void allocatesSequentialSeatsInRegisterNumberOrderAndSkipsExistingStudents() {
        SeatAllocationEngine engine = new SeatAllocationEngine();
        var results = engine.allocate(
                List.of("REG001", "REG002", "REG003"), 2, 2,
                Set.of("REG001"), Set.of("R1-C1"));

        assertEquals(List.of(
                new SeatAllocationEngine.Assignment("REG002", "R1-C2"),
                new SeatAllocationEngine.Assignment("REG003", "R2-C1")
        ), results);
    }

    @Test
    void stopsWhenClassroomCapacityIsReached() {
        SeatAllocationEngine engine = new SeatAllocationEngine();
        var results = engine.allocate(List.of("REG001", "REG002", "REG003"), 1, 2, Set.of(), Set.of());
        assertEquals(2, results.size());
    }
}
