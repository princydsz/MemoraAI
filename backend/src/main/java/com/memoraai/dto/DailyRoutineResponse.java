package com.memoraai.dto;

import java.time.LocalDate;
import java.util.List;

public class DailyRoutineResponse {
    private LocalDate date;
    private List<DailyRoutineItemResponse> morning;
    private List<DailyRoutineItemResponse> afternoon;
    private List<DailyRoutineItemResponse> evening;

    public DailyRoutineResponse(LocalDate date, List<DailyRoutineItemResponse> morning,
                                List<DailyRoutineItemResponse> afternoon,
                                List<DailyRoutineItemResponse> evening) {
        this.date = date;
        this.morning = morning;
        this.afternoon = afternoon;
        this.evening = evening;
    }

    public LocalDate getDate() { return date; }
    public List<DailyRoutineItemResponse> getMorning() { return morning; }
    public List<DailyRoutineItemResponse> getAfternoon() { return afternoon; }
    public List<DailyRoutineItemResponse> getEvening() { return evening; }
}
