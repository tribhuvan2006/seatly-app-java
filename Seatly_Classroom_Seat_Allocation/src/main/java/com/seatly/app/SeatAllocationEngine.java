package com.seatly.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic seat allocator. Students arrive in register-number order. */
public class SeatAllocationEngine {
    public record Assignment(String username, String seatCode) {}

    public List<Assignment> allocate(
            List<String> usernamesInRegisterOrder,
            int rowCount,
            int columnCount,
            Set<String> alreadyAssignedUsernames,
            Set<String> alreadyOccupiedSeats) {
        if (rowCount <= 0 || columnCount <= 0) {
            throw new IllegalArgumentException("Classroom rows and columns must be positive.");
        }
        Set<String> assignedUsers = new HashSet<>(alreadyAssignedUsernames);
        Set<String> usedSeats = new HashSet<>(alreadyOccupiedSeats);
        List<String> available = new ArrayList<>(rowCount * columnCount);
        for (int row = 1; row <= rowCount; row++) {
            for (int column = 1; column <= columnCount; column++) {
                available.add("R" + row + "-C" + column);
            }
        }

        List<Assignment> result = new ArrayList<>();
        int nextSeat = 0;
        for (String username : usernamesInRegisterOrder) {
            if (username == null || username.isBlank() || assignedUsers.contains(username)) continue;
            while (nextSeat < available.size() && usedSeats.contains(available.get(nextSeat))) nextSeat++;
            if (nextSeat >= available.size()) break;
            String seatCode = available.get(nextSeat++);
            usedSeats.add(seatCode);
            assignedUsers.add(username);
            result.add(new Assignment(username, seatCode));
        }
        return result;
    }
}
