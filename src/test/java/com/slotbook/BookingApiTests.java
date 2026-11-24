package com.slotbook;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingApiTests {

    @Autowired
    private MockMvc mvc;

    private static String futureTime(int daysAhead, int hour) {
        return LocalDateTime.now().plusDays(daysAhead).withHour(hour).withMinute(0)
                .withSecond(0).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static String bookingJson(String name, int daysAhead, int hour, int minutes) {
        return "{\"customerName\":\"" + name + "\",\"service\":\"Consultation\","
                + "\"startTime\":\"" + futureTime(daysAhead, hour) + "\","
                + "\"durationMinutes\":" + minutes + "}";
    }

    @Test
    void createsBooking() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Omar", 3, 10, 60)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.customerName", is("Omar")));
    }

    @Test
    void rejectsOverlappingBooking() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Omar", 5, 14, 60)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Sara", 5, 14, 30)))
                .andExpect(status().isConflict());
    }

    @Test
    void allowsCancelledSlotToBeReused() throws Exception {
        String first = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Omar", 7, 9, 60)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(first.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(patch("/api/bookings/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Sara", 7, 9, 60)))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsPastBooking() throws Exception {
        String past = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String json = "{\"customerName\":\"Omar\",\"service\":\"Consultation\","
                + "\"startTime\":\"" + past + "\",\"durationMinutes\":60}";
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsBookings() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Mona", 9, 11, 45)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void filtersBySearchAndStatus() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Omar", 12, 10, 60)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Sara", 12, 13, 60)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/bookings?q=sara"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].customerName", is("Sara")));
        mvc.perform(get("/api/bookings?status=pending"))
                .andExpect(jsonPath("$.content", hasSize(2)));
        mvc.perform(get("/api/bookings?status=confirmed"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void statsCountsBookings() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Omar", 13, 10, 60)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/bookings/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.pending", is(1)))
                .andExpect(jsonPath("$.confirmed", is(0)));
    }

    @Test
    void rejectsUnknownStatusOnUpdate() throws Exception {
        String created = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson("Omar", 11, 16, 60)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(patch("/api/bookings/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }
}
