package com.nerdblistersteam.concierge.controller;

import com.nerdblistersteam.concierge.domain.*;
import com.nerdblistersteam.concierge.service.DescriptionService;
import com.nerdblistersteam.concierge.service.RoomService;
import com.nerdblistersteam.concierge.service.ScheduleService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
public class RoomController {

    private final DescriptionService descriptionService;
    private final RoomService roomService;
    private final ScheduleService scheduleService;

    public RoomController(DescriptionService descriptionService, RoomService roomService, ScheduleService scheduleService) {
        this.descriptionService = descriptionService;
        this.roomService = roomService;
        this.scheduleService = scheduleService;
    }

    @GetMapping("/api/room")
    public @ResponseBody
    List<Room> getRoom() {
        return roomService.findAll();
    }

    @GetMapping("/allrooms")
    public String allrooms(Model model) {
        model.addAttribute("rooms", roomService.findAll());
        return "feed";
    }

    @GetMapping("/createroom")
    public String createroom(Model model) {
        model.addAttribute("rooms", roomService.findAll());
        return "createroom";
    }

    @PostMapping("/createroom")
    public String createNewRoom(@RequestParam String name, @RequestParam int seats, @RequestParam boolean hdmi, @RequestParam boolean whiteboard, @RequestParam boolean projector) {
        Room newRoom = new Room(name, seats);
        if (hdmi) {
            Optional<Description> hdmiDescription = descriptionService.findByTag("HDMI");
            hdmiDescription.ifPresent(newRoom::addDescription);
        }
        if (whiteboard) {
            Optional<Description> whiteboardDescription = descriptionService.findByTag("Whiteboard");
            whiteboardDescription.ifPresent(newRoom::addDescription);
        }
        if (projector) {
            Optional<Description> projectorDescription = descriptionService.findByTag("Projektor");
            projectorDescription.ifPresent(newRoom::addDescription);
        }
        roomService.save(newRoom);
        System.out.println("Skapat rum: " + name);
        return "redirect:/createroom";
    }

    @PostMapping("/deleteroom")
    public String deleteRoom(@RequestParam String deleteName, Model model) {
        Optional<Room> findDelRoom = roomService.findByName(deleteName);
        if (findDelRoom.isPresent()) {
            Room delRoom = findDelRoom.get();
            roomService.delete(delRoom);
            model.addAttribute("success", model.containsAttribute("success"));
        } else {
            model.addAttribute("danger", model.containsAttribute("danger"));
        }

        return "redirect:/createroom";
    }

    //Denna funktion tar ett värde från allrooms och visar sedan lediga och bokade tider för rummet.
    //Koden bör inte ligga här, kanske bättre placering i RoomService.
    @GetMapping("/room/{name}")
    public String room(@PathVariable String name, Model model) {

        Optional<Room> currentRoomFind = roomService.findByName(name);

        if (currentRoomFind.isPresent()) {
            Room currentRoom = currentRoomFind.get();

            model.addAttribute("room", currentRoom);
            model.addAttribute("descriptions", currentRoom.getDescriptions());
        }

        //Bokningsfönstret per dag
        LocalDateTime openBooking = LocalDateTime.of(LocalDate.now(), LocalTime.of(6, 0));
        LocalDateTime closeBooking = LocalDateTime.of(LocalDate.now(), LocalTime.of(17, 0));

        List<Timespann> booked = new ArrayList<>();
        List<Timespann> free = new ArrayList<>();
        List<Timespann> freeAndBooked = new ArrayList<>();
        List<Schedule> schedulesFromDB = scheduleService.findAll();


        for (int i = 0; i < schedulesFromDB.size(); i++) {
            if (schedulesFromDB.get(i).getRoom().getName().equals(name) & schedulesFromDB.get(i).getStart().isAfter(openBooking)) {
                booked.add(new Timespann(schedulesFromDB.get(i).getStart(), schedulesFromDB.get(i).getStop(), true));
            }

        }

        booked.sort(Comparator.comparing(Timespann::getStart));

        for (int i = 0; i < booked.size(); i++) {
            if (i == 0) {
                if (booked.get(i).getStart().isAfter(openBooking)) {
                    freeAndBooked.add(new Timespann(openBooking, booked.get(i).getStart(), false));
                }

            }

            if (i < (booked.size() - 1)) {
                if (booked.get(i).getStop().isEqual(booked.get(i + 1).getStart())) {
                    System.out.println("bokning är direkt efter");
                }

            }

            if (i < (booked.size() - 1)) {
                if (booked.get(i).getStop().isBefore(booked.get(i + 1).getStart())) {
                    freeAndBooked.add(new Timespann(booked.get(i).getStop(), booked.get(i + 1).getStart(), false));
                }

            }

            if (i == (booked.size() - 1)) {
                if (booked.get(i).getStop().isBefore(closeBooking)) {
                    freeAndBooked.add(new Timespann(booked.get(i).getStop(), closeBooking, false));
                }

            }
        }

        for (int i = 0; i < booked.size(); i++) {
            freeAndBooked.add(booked.get(i));
        }

        freeAndBooked.sort(Comparator.comparing(Timespann::getStart));


        model.addAttribute("roomName", name);
        model.addAttribute("times", free);
        model.addAttribute("bookings", freeAndBooked);
        return "Rum";
    }

    //Skapar bokning för ett specifikt rum och visar bokade och lediga tider. Just nu kan man bara boka den dag som dagens datum.
    //Här ska koppling göras till användare och rum, då det nu är hårdkodat.
    @PostMapping("/room/createbooking")
    public String createNewBooking(@RequestParam String roomName, @RequestParam LocalTime start, @RequestParam LocalTime stop, RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        Optional<Room> findRoom = roomService.findByName(roomName);
        Timespann createdBooking = new Timespann(LocalDate.now().atTime(start), LocalDate.now().atTime(stop), true);
        if (findRoom.isPresent()) {
            Room room1 = findRoom.get();
            String name = room1.getName();
            System.out.println(createdBooking.getStart());
            System.out.println(createdBooking.getStop());
            Schedule add = new Schedule(createdBooking.getStart(), createdBooking.getStop());
            System.out.println(add.getStart());
            System.out.println(add.getStop());
            add.addUser(currentUser);
            add.addRoom(room1);

            scheduleService.save(add);

            redirectAttributes
                    .addAttribute("name", room1.getName())
                    .addFlashAttribute("success", true);
            return "redirect:/room/{name}";
        } else {
            redirectAttributes
                    .addFlashAttribute("danger", true);
            return "redirect:/allrooms";
        }
    }
}
