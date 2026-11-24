# SlotBook, in plain words

## What it is

A booking system backend: the server part of an app where customers book
time slots (a clinic appointment, a tutoring session, a barber chair).
There is also a simple web page (served by the same app at the root URL)
with stats cards (total, pending, confirmed, cancelled, today), a bookings
table you can search, filter by status or day, and page through, plus a
form for new bookings with clear error messages. It calls the same API a
mobile app would use.

## What it does

- Create a booking: customer name, what service, when it starts, how long.
- The server refuses a booking if its time overlaps an existing active
  booking, so nobody can be double-booked.
- The server refuses bookings in the past and durations under 15 minutes.
- Each booking has a status: it starts as PENDING, can be CONFIRMED, or
  CANCELLED. A cancelled slot becomes free again.

## The technology

- Java with Spring Boot, the standard enterprise Java framework. Same
  stack as the Library Management System project on this GitHub account.
- Spring Data JPA stores bookings in a database (H2, an in-memory
  database, so it runs with zero setup).
- Validation annotations keep bad data out before any logic runs.

## Tests

`mvn test` runs 6 automated checks that prove the booking rules work:
overlap is rejected, cancelled slots free up, past dates are rejected,
listing works, and nonsense status values are rejected.

## If a client asks about it

Talking points that are true:

- REST API design with proper status codes (201 created, 409 conflict,
  400 bad request).
- Business rule enforced on the server: no double-booking.
- Bean Validation for input, JPA for persistence.
- Automated tests with MockMvc covering the main flows.
