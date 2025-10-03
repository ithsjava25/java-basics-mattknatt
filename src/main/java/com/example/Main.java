package com.example;

import com.example.api.ElpriserAPI;
import com.example.api.ElpriserAPI.*;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static com.example.api.ElpriserAPI.Prisklass.*;

public class Main {

    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();
        String zoneArg = null;
        Prisklass zone;
        String dateArg = null;
        boolean sorted = false;
        String chargingArg = null;


        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone" -> {
                    if (i + 1 < args.length) {
                        zoneArg = args[i + 1];
                        i++;
                    } else {
                        System.out.println("Error: --zone requires a value");
                        return;
                    }
                }
                case "--date" -> {
                    if (i + 1 < args.length) {
                        dateArg = args[i + 1];
                        i++;
                    }
                }
                case "--sorted" -> sorted = true;
                case "--charging" -> {
                    if (i + 1 < args.length) {
                        chargingArg = args[i + 1];
                        i++;
                    }
                }
                case "--help" -> {
                    printHelp();
                    return;
                }
                default -> {
                    System.out.println("Invalid argument: " + args[i]);
                    printHelp();
                }
            }
        }
        if (zoneArg == null) {
            System.out.println("Error: --zone is required");
            printHelp();
            return;
        } else if (!zoneArg.trim().equals("SE1") &&
                !zoneArg.trim().equals("SE2") &&
                !zoneArg.trim().equals("SE3") &&
                !zoneArg.trim().equals("SE4")) {
            System.out.println("Invalid zone");
        }

        switch (zoneArg) {
            case "SE1" -> zone = SE1;
            case "SE2" -> zone = SE2;
            case "SE4" -> zone = SE4;
            default -> zone = SE3;

        }
        LocalDate today;

        if (dateArg == null) {
            today = LocalDate.now();
        } else {
            try {
                today = LocalDate.parse(dateArg, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date");
                return;
            }
        }


        LocalDate tomorrow = today.plusDays(1);

        List<Elpris> prices = elpriserAPI.getPriser(today, zone);

        if (prices.size() == 96) {
            prices = convertListToHourly(prices);
        }

        List<Elpris> tomorrowsPrices = new ArrayList<>();

        if ((LocalDate.now().equals(today) && LocalTime.now().isAfter(LocalTime.of(13, 0))) ||
                today.isBefore(LocalDate.now())) {
            tomorrowsPrices = elpriserAPI.getPriser(tomorrow, zone);
            if (tomorrowsPrices.size() == 96) {
                tomorrowsPrices = convertListToHourly(tomorrowsPrices);
            }
        }


        if (prices.isEmpty() && tomorrowsPrices.isEmpty()) {
            System.out.println("Inga priser tillgängliga.");
            return;
        }

        List<Elpris> allPrices = new ArrayList<>(prices);


        //Skriv ut Min, Max, Mean
        if (tomorrowsPrices.isEmpty()) {
            printMinMaxMean(prices);
        } else {
            allPrices.addAll(tomorrowsPrices);
            printMinMaxMean(allPrices);
        }
        // Skriv ut laddningsfönster
        if (chargingArg != null) {
            findOptimalChargingWindow(allPrices, chargingArg);
        }
        //Skriv ut sorterad lista
        if (sorted) {
            printSortedPrices(allPrices);
        }
    }

    private static List<Elpris> convertListToHourly(List<Elpris> prices) {
        List<Elpris> hourlyPrices = new ArrayList<>();

        for (int i = 0; i < prices.size(); i += 4) {
            double sekSum = 0;
            double eurSum = 0;
            double exrSum = 0;

            // summera fyra kvartstimmar
            for (int j = 0; j < 4; j++) {
                Elpris p = prices.get(i + j);
                sekSum += p.sekPerKWh();
                eurSum += p.eurPerKWh();
                exrSum += p.exr();
            }

            // medelvärden
            double sekAvg = sekSum / 4.0;
            double eurAvg = eurSum / 4.0;
            double exrAvg = exrSum / 4.0;

            Elpris first = prices.get(i);
            Elpris last = prices.get(i + 3);

            // skapa nytt timobjekt
            Elpris hourly = new Elpris(
                    sekAvg,
                    eurAvg,
                    exrAvg,
                    first.timeStart(),
                    last.timeEnd()
            );

            hourlyPrices.add(hourly);
        }

        return hourlyPrices;
    }

    private static void printHelp() {
        System.out.println("Usage: ");
        System.out.println("--zone SE1|SE2|SE3|SE4 (required)");
        System.out.println("--date YYYY-MM-DD (optional, today's date as default)");
        System.out.println("--sorted (optional)");
        System.out.println("--charging 2h|4h|8h (optional)");
        System.out.println("--help");
    }

    private static void printMinMaxMean(List<Elpris> priser) {
        DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("HH");
        double sum = 0;
        Elpris billigast = priser.getFirst();
        Elpris dyrast = priser.getFirst();

        for (Elpris p : priser) {
            double price = p.sekPerKWh();
            sum += price;
            if (price < billigast.sekPerKWh()) billigast = p;
            if (price > dyrast.sekPerKWh()) dyrast = p;
        }

        String billigastTid = billigast.timeStart().format(hourFormatter) + "-" + billigast.timeEnd().format(hourFormatter);
        String dyrastTid = dyrast.timeStart().format(hourFormatter) + "-" + dyrast.timeEnd().format(hourFormatter);


        double mean = sum / priser.size();
        DecimalFormat df = swedishDecimalFormat();

        System.out.printf("Medelpris: %s öre\n", df.format(mean * 100));
        System.out.printf("Lägsta Pris: %s (%s öre)\n", billigastTid, df.format(billigast.sekPerKWh() * 100));
        System.out.printf("Högsta Pris: %s (%s öre)\n", dyrastTid, df.format(dyrast.sekPerKWh() * 100));
    }

    private static void printSortedPrices(List<Elpris> priser) {
        // Sortera listan i stigande ordning efter pris
        List<Elpris> sortedList = new ArrayList<>(priser);
        sortedList.sort(Comparator.comparingDouble(Elpris::sekPerKWh).reversed());

        DecimalFormat df = swedishDecimalFormat();

        System.out.println("\nPriser sorterade efter pris (fallande):");
        for (ElpriserAPI.Elpris p : sortedList) {
            System.out.printf("%02d-%02d %s öre\n", p.timeStart().getHour(), p.timeEnd().getHour(), df.format(p.sekPerKWh() * 100));
        }
    }

    private static void findOptimalChargingWindow(List<Elpris> priser, String chargingArg) {
        int hours = 0;
        switch (chargingArg) {
            case "2h" -> hours = 2;
            case "4h" -> hours = 4;
            case "8h" -> hours = 8;
            default -> System.out.println("Invalid charging argument " + chargingArg);
        }

        double minAvgPrice = Double.MAX_VALUE;
        int bestStartIndex = -1;

        // Iterera genom alla möjliga startpositioner för fönstret
        // priser.size() - hours ger den sista index där fönstret (hours långt) ryms
        for (int i = 0; i <= priser.size() - hours; i++) {
            double currentSum = 0;
            for (int j = 0; j < hours; j++) {
                currentSum += priser.get(i + j).sekPerKWh();
            }

            double currentAvg = currentSum / hours;

            if (currentAvg < minAvgPrice) {
                minAvgPrice = currentAvg;
                bestStartIndex = i;
            }
        }

        if (bestStartIndex != -1) {
            Elpris startPris = priser.get(bestStartIndex);
            // Slutpriset för fönstret är startIndex + hours - 1, och vi tar timeEnd().
            Elpris endPris = priser.get(bestStartIndex + hours - 1);

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DecimalFormat df = swedishDecimalFormat();

            System.out.println("\nOptimalt laddningsfönster (" + hours + "h):");
            System.out.printf("Påbörja laddning kl %s\n", startPris.timeStart().format(timeFormatter));
            System.out.printf("Sluta ladda kl %s\n", endPris.timeEnd().format(timeFormatter));
            System.out.printf("Medelpris för fönster: %s öre\n", df.format(minAvgPrice * 100));
        } else {
            System.out.println("Kunde inte hitta ett optimalt laddningsfönster.");
        }
    }

    private static DecimalFormat swedishDecimalFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("sv", "SE"));
        return new DecimalFormat("0.00", symbols);
    }
}



